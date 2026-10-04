"""Independent audit of conserved evidence; does not execute the model."""
import argparse
import collections
import csv
import hashlib
import json
import math
import os
import random
import re
import statistics
import sys
from decimal import Decimal, InvalidOperation
from pathlib import Path
from output_paths import evaluation_dir

# Required assertions cannot be disabled in a certification utility.
if sys.flags.optimize:
    raise RuntimeError('Evidence audit must run without Python optimization')

XSD = 'http://www.w3.org/2001/XMLSchema#'
NUMERICS = {XSD+x for x in ['integer','decimal','double','float','int','long','short','nonNegativeInteger']}
CORE_METRICS = ['total_ms','load_ms','reasoning_stage_ms','query_stage_ms','wall_process_ms',
                'observed_peak_process_tree_rss_bytes','raw_input_triples','input_triples',
                'materialized_triples','input_named_individuals','materialized_named_individuals',
                'dl_ms','dl_materialized_triples','construct_added_triples']
POST_METRICS = ['post_swrl_dl_ms','post_swrl_dl_materialized_triples']
WORKER_METRICS = ['swrl_ms','new_axiom_count','engine_inferred_axiom_count','imported_rule_count']
COUNTERS = {'observed_peak_process_tree_rss_bytes','raw_input_triples','input_triples',
            'materialized_triples','input_named_individuals','materialized_named_individuals',
            'dl_materialized_triples','construct_added_triples','post_swrl_dl_materialized_triples',
            'new_axiom_count','engine_inferred_axiom_count','imported_rule_count','active_rule_count'}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text())


def finite_nonnegative(value):
    return isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and value>=0


def nonnegative_integer(value):
    return type(value) is int and value>=0


def java17(value):
    return isinstance(value,str) and re.fullmatch(r'17(?:\.\d+)*(?:[+_-][A-Za-z0-9._-]+)?',value) is not None


def environment_java(environment):
    match=re.search(r'\bversion\s+"([^"\n]+)"',environment['java_version'])
    assert match and java17(match.group(1)), 'Environment must identify Java17'
    return match.group(1)


def require_safe_output(repo,path,evaluation=None):
    """Writing evidence must never alter a frozen input, binary or raw record."""
    destination=path.resolve()
    protected=['ontologies','research/model','research/data','research/runtime','research/evaluation/current','research/evaluation/runs']
    assert not any(destination.is_relative_to((repo/relative).resolve()) for relative in protected), 'Output belongs to protected source or evidence tree: '+str(path)
    if evaluation is not None:
        assert not destination.is_relative_to(evaluation.resolve()), 'Output belongs to selected raw evidence'
    fixed=['research/catalog.json','research/reproduce.sh']
    fixed += [str(file.relative_to(repo)) for file in (repo/'research/evaluation').glob('*.py') if file.is_file()]
    assert destination not in {(repo/relative).resolve() for relative in fixed}, 'Output would replace an evaluated input'
    assert not (destination.parent==(repo/'research/evaluation').resolve() and destination.suffix=='.py'), 'Output would become an evaluated Python input'


def live_source_hashes(repo):
    result={}
    for relative in ['ontologies','research/model','research/data','research/runtime']:
        for path in (repo/relative).rglob('*'):
            if path.is_file() and 'target' not in path.parts and '__pycache__' not in path.parts:
                result[str(path.relative_to(repo))]=digest(path)
    for path in [repo/'research/catalog.json',repo/'research/reproduce.sh']+sorted((repo/'research/evaluation').glob('*.py')):
        assert path.is_file() and not path.is_symlink(), path
        result[str(path.relative_to(repo))]=digest(path)
    return result


def live_runtime_hashes(repo):
    result={}
    for module in ['validator','swrl-worker']:
        target=repo/'research/runtime'/module/'target'
        classes=list((target/'classes').rglob('*.class'))
        assert classes, 'Missing compiled classes: '+module
        for path in classes:
            result[str(path.relative_to(repo))]=digest(path)
        classpath=target/'classpath.txt'
        assert classpath.is_file(), classpath
        for item in classpath.read_text().strip().split(os.pathsep):
            path=Path(item)
            assert path.is_file(), path
            result['maven/'+str(path).split('/repository/',1)[-1]]=digest(path)
    return result


def rows_equal(actual,expected):
    if 'boolean' in expected:
        return type(actual.get('boolean')) is bool and type(expected['boolean']) is bool and actual['boolean']==expected['boolean']
    if set(actual.get('head',{}).get('vars',[]))!=set(expected.get('head',{}).get('vars',[])):
        return False
    def term_equal(a,b):
        if a.get('type')!=b.get('type'):
            return False
        if a.get('type')=='literal' and a.get('datatype') in NUMERICS and b.get('datatype') in NUMERICS:
            if 'xml:lang' in a or 'xml:lang' in b:
                return False
            try:
                x,y=Decimal(a['value']),Decimal(b['value'])
                return x.is_finite() and y.is_finite() and abs(x-y)<=max(Decimal('1e-9'),Decimal('1e-9')*max(abs(x),abs(y)))
            except (InvalidOperation,KeyError,TypeError,ValueError):
                return False
        if a.get('type')=='literal' and 'xml:lang' not in a and 'xml:lang' not in b:
            if a.get('datatype',XSD+'string')==b.get('datatype',XSD+'string')==XSD+'string':
                return a.get('value')==b.get('value')
        return a==b
    obtained=actual.get('results',{}).get('bindings',[])
    required=expected.get('results',{}).get('bindings',[])
    if len(obtained)!=len(required):
        return False
    edges=[[i for i,row in enumerate(obtained) if row.keys()==target.keys() and all(term_equal(row[k],v) for k,v in target.items())] for target in required]
    owner={}
    def augment(index,seen):
        for candidate in edges[index]:
            if candidate in seen:
                continue
            seen.add(candidate)
            if candidate not in owner or augment(owner[candidate],seen):
                owner[candidate]=index
                return True
        return False
    return all(augment(index,set()) for index in range(len(required)))


def expected_request(catalog,cfg,benchmark=False):
    numeric_controls=cfg['numeric_controls_enabled']
    assert type(numeric_controls) is bool, 'Numeric control applicability must be a boolean'
    files=list(cfg.get('files',cfg.get('tbox_paths',[])+cfg.get('abox_paths',[])+cfg.get('rules_paths',[])))
    if benchmark:
        loads=cfg['benchmark_abox']
        loads=loads if isinstance(loads,list) else [loads]
        files=[x for x in files if x not in cfg.get('abox_paths',[])]+loads
    ruleids=cfg.get('enabled_rule_ids',[])
    enabled=cfg.get('enabled_rules','ALL' if ruleids=='ALL' else ','.join(ruleids) if ruleids else 'NONE')
    if isinstance(enabled,list):
        enabled=','.join(enabled) or 'NONE'
    queries=[{'id':q['id'],'path':q['path']} for q in catalog['queries'] if q['id'] in cfg.get('query_ids',[q['id'] for q in catalog['queries']])]
    queries += [{'id':q['id'],'path':q['path']} for q in catalog['validations'] if q['id'] in cfg.get('validation_ids',[q['id'] for q in catalog['validations']])]
    return {'files':list(dict.fromkeys(files)),'enabled_rules':enabled,'queries':queries,
            'pre_inference_constructs':catalog.get('pre_inference_constructs',[]) if cfg.get('run_pre_inference_constructs') else [],
            'numeric_controls_enabled':numeric_controls}


def raw_inventory(repo,directory):
    assert directory.is_dir() and not directory.is_symlink(), directory
    records=[]
    for path in sorted(directory.rglob('*')):
        assert not path.is_symlink(), path
        if path.is_file():
            records.append({'path':str(path.relative_to(repo)),'bytes':path.stat().st_size,'sha256':digest(path)})
    return records


def fixture_groups(catalog,fixture):
    """Reconstruct fixture membership independently of reported group paths."""
    configurations={cfg['id']:cfg for cfg in catalog['configurations']}
    groups={}
    for case in fixture['assertions']:
        request=expected_request(catalog,configurations[case['configuration']])
        request['files']+=case.get('additional_files',[])
        if case.get('files'):
            request['files']=list(case['files'])
        if case.get('enabled_rule_ids') is not None:
            request['enabled_rules']=','.join(case['enabled_rule_ids']) or 'NONE'
        if case.get('enabled_rules') is not None:
            request['enabled_rules']=case['enabled_rules']
        if isinstance(request['enabled_rules'],list):
            request['enabled_rules']=','.join(request['enabled_rules']) or 'NONE'
        assert isinstance(request['enabled_rules'],str), 'Rule selection must be textual after fixture overrides'
        request['queries']=[]
        key=(tuple(request['files']),request['enabled_rules'],json.dumps(request['pre_inference_constructs'],sort_keys=True),request['numeric_controls_enabled'])
        groups.setdefault(key,{'request':request,'cases':[]})['cases'].append(case)
    for group in groups.values():
        group['request']['queries']=[{'id':case['id'],'path':case['query_path']} for case in group['cases'] if case.get('query_path')]
    return list(groups.values())


def audit_negative_group(repo,directory,request,cases):
    """Negative fixtures stop at their defined stage; no successful run is accepted."""
    assert read(directory/'request.json')==request
    assert isinstance(request['enabled_rules'],str), 'A rejected run must still have a valid rule selection request'
    execution=read(directory/'execution.json')
    validation=read(directory/'validation.json')
    expected={case['expected_status'] for case in cases}
    assert len(expected)==1 and execution['status'] in expected and validation['status'] in expected
    assert type(execution['exit_code']) is int
    assert execution['validation']==validation
    assert execution['last_completed_validation']==validation
    later=['run.json','result-model.ttl','combined.owl','swrl','swrl-stdout.txt','swrl-stderr.txt',
           'validation-after-swrl.json','dl-post-deductions.ttl','exchange-preservation-error.json']
    assert not any((directory/name).exists() for name in later), 'A fixture failing before materialization contains later-stage artifacts'
    assert request['queries']==[], 'A failed group must not pretend to execute ASK results'
    required=['request.json','execution.json','stdout.txt','stderr.txt','validation.json']
    load_failure=any(case['id']=='Failure_load_error' for case in cases)
    numeric_failure=any(case.get('expected_numeric_status')=='AMBIGUOUS_INPUT' for case in cases)
    if load_failure:
        assert execution['failure_kind']=='LOAD_ERROR' and validation['failure_kind']=='LOAD_ERROR'
        assert execution['exit_code']==2 and validation['profile_valid'] is False and validation.get('error')
        assert 'org.apache.jena.riot.RiotException:' in validation['error'] and 'Not a valid token for an RDF term' in validation['error'], 'Defined invalid-syntax fixture must fail in its RDF parser'
        assert not (directory/'raw-input.owl').exists()
        assert execution['owl_validation_status']==validation['owl_validation_status']=='NOT_EVALUATED'
    elif numeric_failure:
        assert all(case.get('expected_numeric_status')=='AMBIGUOUS_INPUT' for case in cases)
        assert execution['status']==validation['status']=='NOT_EVALUATED'
        assert execution['exit_code']==3
        assert execution['failure_kind']==validation['failure_kind']=='AMBIGUOUS_NUMERIC_INPUT'
        assert execution['owl_validation_status']==validation['owl_validation_status']=='CONSISTENT'
        assert validation['profile_valid'] is True and validation['profile_violations']==[]
        numeric=validation['numeric_evaluation']
        assert execution['numeric_evaluation']==numeric
        assert numeric['status']=='AMBIGUOUS_INPUT' and numeric['ambiguous_input'] is True
        assert nonnegative_integer(numeric['selected_operation_count']) and numeric['selected_operation_count']>0
        assert numeric['output_checked'] is False and isinstance(numeric['reason'],str) and numeric['reason']
        assert execution['error']==validation['error']==numeric['reason']
        audit_retention(validation['dl_axiom_preservation'])
        assert finite_nonnegative(validation['dl_ms'])
        required+=['raw-input.owl','prepared-input.ttl','dl-pre-deductions.ttl']
    else:
        assert execution['exit_code']==3
        assert validation['profile_valid'] is (execution['status']=='INCONSISTENT')
        kind='OWL_INCONSISTENT' if execution['status']=='INCONSISTENT' else 'OWL_PROFILE_INVALID'
        assert execution['failure_kind']==validation['failure_kind']==kind
        assert execution['owl_validation_status']==validation['owl_validation_status']==execution['status']
        violations=validation['profile_violations']
        assert isinstance(violations,list)
        if execution['status']=='NOT_EVALUATED':
            assert violations and all(isinstance(value,str) and value for value in violations), 'Profile failure requires conserved violations'
        else:
            assert violations==[]
        required+=['raw-input.owl','prepared-input.ttl','dl-pre-deductions.ttl']
    assert all((directory/name).is_file() for name in required)
    assert finite_nonnegative(execution['wall_process_ms'])
    assert nonnegative_integer(execution['observed_peak_process_tree_rss_bytes'])
    assert execution['rss_sampling_interval_ms']==100 and nonnegative_integer(execution['rss_sampling_interval_ms'])
    records=raw_inventory(repo,directory)
    actual_files={str((repo/record['path']).relative_to(directory)) for record in records}
    assert actual_files==set(required), (directory,'Unexpected or missing artifacts in a defined pre-worker failure',actual_files-set(required),set(required)-actual_files)
    assert not any(path.is_dir() for path in directory.rglob('*')), 'Pre-worker failure must not contain later-stage directories'
    return execution,records


def audit_retention(record):
    assert record['status']=='RETAINED'
    assert record['comparison']=='EXACT_STRUCTURAL_AXIOM_SET_INCLUSION'
    for key in ['expected_axiom_count','observed_axiom_count','retained_axiom_count','lost_axiom_count',
                'expected_anonymous_individual_count','observed_anonymous_individual_count']:
        assert nonnegative_integer(record[key]), key
    assert record['lost_axiom_count']==0
    assert record['retained_axiom_count']==record['expected_axiom_count']
    assert record['observed_axiom_count']>=record['expected_axiom_count']
    assert record['observed_anonymous_individual_count']>=record['expected_anonymous_individual_count']


def audit_execution(repo,directory,request,expected_rules,expected_java=None):
    assert read(directory/'request.json')==request, directory
    execution=read(directory/'execution.json')
    internal=read(directory/'run.json')
    assert execution['status']=='CONSISTENT' and execution['exit_code']==0
    assert execution['owl_validation_status']=='CONSISTENT'
    assert execution['numeric_evaluation']['status'] in {'VALID','NOT_APPLICABLE','NOT_EVALUABLE'}
    assert type(execution['exit_code']) is int
    assert execution['profile_valid'] is True and execution['rss_sampling_interval_ms']==100
    assert nonnegative_integer(execution['rss_sampling_interval_ms'])
    assert java17(execution['java']), (directory,'java')
    if expected_java is not None:
        assert execution['java']==expected_java, (directory,'java/environment mismatch')
    assert all(execution.get(k)==v for k,v in internal.items())
    pre=read(directory/'validation.json')
    assert pre['status']=='CONSISTENT' and pre['profile_valid'] is True
    assert execution['dl_axiom_preservation']==pre['dl_axiom_preservation']
    audit_retention(pre['dl_axiom_preservation'])
    final_validation=pre
    worker_expected=request['enabled_rules']!='NONE'
    assert bool(execution.get('swrl'))==worker_expected
    required=['request.json','execution.json','run.json','stdout.txt','stderr.txt',
              'raw-input.owl','prepared-input.ttl','combined.owl','dl-pre-deductions.ttl',
              'validation.json','result-model.ttl']
    if worker_expected:
        assert execution['exchange_hashes_verified'] is True
        post=read(directory/'validation-after-swrl.json')
        final_validation=post
        assert post['status']=='CONSISTENT' and post['profile_valid'] is True
        assert execution['post_swrl_dl_axiom_preservation']==post['dl_axiom_preservation']
        audit_retention(post['dl_axiom_preservation'])
        exchange=read(directory/'swrl/swrl.json')
        assert exchange==execution['swrl']
        assert exchange['status']=='EXECUTED'
        retention=exchange['axiom_preservation']
        assert retention['status']=='RETAINED'
        for key in ['input_non_swrl_axiom_count','post_inference_non_swrl_axiom_count','input_anonymous_individual_count','intentionally_removed_swrl_rule_count']:
            assert nonnegative_integer(retention[key]), key
        assert finite_nonnegative(retention['verification_and_serialization_ms'])
        for key in ['after_inference','saved_materialized_reload','saved_deductions_reload']:
            audit_retention(retention[key])
        before=retention['input_non_swrl_axiom_count']
        assert before==pre['dl_axiom_preservation']['observed_axiom_count']
        assert retention['after_inference']['expected_axiom_count']==before
        inferred=retention['post_inference_non_swrl_axiom_count']
        assert inferred==retention['after_inference']['observed_axiom_count']==retention['saved_materialized_reload']['expected_axiom_count']
        assert retention['saved_deductions_reload']['expected_axiom_count']==exchange['new_axiom_count']
        assert exchange['imported_rule_count']+retention['intentionally_removed_swrl_rule_count']==pre['swrl_rule_count']
        audit_retention(execution['exchange_axiom_preservation'])
        audit_retention(execution['exchange_input_axiom_preservation'])
        assert nonnegative_integer(execution['exchange_loaded_non_swrl_axiom_count'])
        loaded=execution['exchange_loaded_non_swrl_axiom_count']
        assert loaded==retention['saved_materialized_reload']['observed_axiom_count']==execution['exchange_axiom_preservation']['expected_axiom_count']
        assert execution['exchange_input_axiom_preservation']['expected_axiom_count']==before
        assert execution['exchange_input_axiom_preservation']['observed_axiom_count']==execution['exchange_axiom_preservation']['observed_axiom_count']
        assert execution['exchange_axiom_preservation']['observed_axiom_count']==post['dl_axiom_preservation']['expected_axiom_count']
        assert exchange['imported_rule_count']==exchange['active_rule_count']==expected_rules
        assert exchange['swrlapi']=='2.1.3' and exchange['owlapi']=='4.5.27' and exchange['drools']=='7.74.1.Final'
        for relative,field in [('combined.owl','input_sha256'),('swrl/materialized.owl','materialized_sha256'),('swrl/deductions.owl','deductions_sha256')]:
            assert digest(directory/relative)==exchange[field]
        for metric in WORKER_METRICS:
            assert finite_nonnegative(exchange[metric]), (directory,metric)
            if metric in COUNTERS:
                assert nonnegative_integer(exchange[metric]), (directory,metric)
        assert nonnegative_integer(exchange['active_rule_count'])
        required += ['validation-after-swrl.json','dl-post-deductions.ttl','swrl-stdout.txt','swrl-stderr.txt',
                     'swrl/swrl.json','swrl/materialized.owl','swrl/deductions.owl']
    assert execution['owl_validation_status']==final_validation['owl_validation_status']=='CONSISTENT'
    for field in ['numeric_evaluation','cleaning_evaluation','context_evaluation']:
        assert execution[field]==final_validation[field], (directory,field,'must match last completed validation')
    assert final_validation['numeric_evaluation']['output_checked'] is (request['numeric_controls_enabled'] and worker_expected), (directory,'numeric output control stage')
    ids=[q['id'] for q in request['queries']]
    assert len(ids)==len(set(ids))
    assert collections.Counter(q['id'] for q in execution['queries'])==collections.Counter(ids)
    for query in execution['queries']:
        obtained=read(directory/(query['id']+'.json'))
        assert nonnegative_integer(query['result_count']), (directory,query['id'],'result_count')
        assert query['result_count']==len(obtained.get('results',{}).get('bindings',[]))
        if 'boolean' in obtained:
            assert type(obtained['boolean']) is bool and query['boolean'] is obtained['boolean']
        assert finite_nonnegative(query['query_ms'])
    required += [ident+'.json' for ident in ids]
    for relative in required:
        assert (directory/relative).is_file(), (directory,relative)
    for metric in CORE_METRICS+(POST_METRICS if worker_expected else []):
        assert finite_nonnegative(execution[metric]), (directory,metric)
        if metric in COUNTERS:
            assert nonnegative_integer(execution[metric]), (directory,metric)
    assert execution['observed_peak_process_tree_rss_bytes']>0, (directory,'No positive RSS observation for a completed execution')
    if not worker_expected:
        assert not any(metric in execution for metric in POST_METRICS)
    assert execution['load_ms']+execution['reasoning_stage_ms']+execution['query_stage_ms']<=execution['total_ms']+1
    assert execution['total_ms']<=execution['wall_process_ms']+1
    return execution,raw_inventory(repo,directory)


def audit_functional(repo,catalog,functional,out):
    assert functional['status']=='PASS'
    assert functional['source_hashes_unchanged'] is True and functional['runtime_hashes_unchanged'] is True
    assert functional['input_hashes_before']==functional['input_hashes_after']==functional['input_hashes']
    assert functional['runtime_hashes_before']==functional['runtime_hashes_after']==functional['runtime_hashes']
    configs=[c for c in catalog['configurations'] if not c.get('benchmark_only')]
    assert len(configs)==5
    ids={c['id'] for c in configs}
    assert {path.name for path in (out/'functional').iterdir() if path.is_dir()}==ids, 'Functional directory contains missing or stale configurations'
    assert collections.Counter(x['id'] for x in functional['configurations'])=={x:1 for x in ids}
    assert collections.Counter(x['configuration'] for x in functional['rule_import_checks'])=={x:1 for x in ids}
    imports={x['configuration']:x for x in functional['rule_import_checks']}
    expected_java=environment_java(read(out/'environment.json'))
    evidence=[]
    for cfg in configs:
        result,records=audit_execution(repo,out/'functional'/cfg['id'],expected_request(catalog,cfg),len(cfg['enabled_rule_ids']),expected_java)
        assert result['numeric_evaluation']['status']==('VALID' if cfg['numeric_controls_enabled'] else 'NOT_APPLICABLE'), (cfg['id'],'numeric configuration expectation')
        assert next(x['result'] for x in functional['configurations'] if x['id']==cfg['id'])==result
        check=imports[cfg['id']]
        assert check['pass'] is True and check['expected']==check['imported']==len(cfg['enabled_rule_ids'])
        selected=set(query['id'] for query in expected_request(catalog,cfg)['queries'])
        for validation in catalog['validations']:
            if validation['id'] in selected:
                assert type(validation['expected']) is bool
                assert read(out/'functional'/cfg['id']/(validation['id']+'.json'))['boolean'] is validation['expected'], (cfg['id'],validation['id'],'canonical ASK expectation')
        evidence.append({'kind':'configuration','id':cfg['id'],'raw_files':records})
    required={(c['id'],q['id']) for c in configs for q in catalog['queries']}
    assert len(required)==105
    checks=functional['golden_checks']
    assert collections.Counter((x['configuration'],x['id']) for x in checks)=={x:1 for x in required}
    for check in checks:
        cfg=next(c for c in configs if c['id']==check['configuration'])
        query=next(q for q in catalog['queries'] if q['id']==check['id'])
        expectation_cfg=cfg.get('expected_results_configuration',cfg['id'])
        canonical=query.get('expected_paths',{}).get(expectation_cfg,query.get('expected_path') if expectation_cfg=='integrated' else None)
        assert canonical and check['expected_path']==canonical and check['pass'] is True
        actual=read(out/'functional'/cfg['id']/(query['id']+'.json'))
        expected=read(repo/canonical)
        assert rows_equal(actual,expected), check
        assert check['expected_rows']==len(expected.get('results',{}).get('bindings',[]))
        assert check['actual_rows']==len(actual.get('results',{}).get('bindings',[]))
    fixture=read(repo/'research/data/fixtures/tests.json')
    assertions=functional['assertions']
    assert len(fixture['assertions'])==len({x['id'] for x in fixture['assertions']})
    assert functional['expected_assertion_count']==len(fixture['assertions'])
    assert nonnegative_integer(functional['expected_assertion_count'])
    assert collections.Counter(x['id'] for x in assertions)==collections.Counter(x['id'] for x in fixture['assertions'])
    by_id={x['id']:x for x in assertions}
    groups=fixture_groups(catalog,fixture)
    assert {path.name for path in (out/'assertion-groups').iterdir() if path.is_dir()}=={f'g{number:02d}' for number in range(1,len(groups)+1)}, 'Fixture directory contains missing or stale groups'
    for number,group in enumerate(groups,1):
        directory=out/'assertion-groups'/f'g{number:02d}'
        cases=group['cases'];request=group['request']
        negative=any('expected_status' in case and case['expected_status']!='CONSISTENT' for case in cases)
        if negative:
            assert all('expected_status' in case and case['expected_status']!='CONSISTENT' for case in cases)
            execution,records=audit_negative_group(repo,directory,request,cases)
        else:
            enabled=request['enabled_rules']
            assert enabled!='ALL', 'An assertion group must name its enabled rules explicitly'
            count=0 if enabled in {'NONE','OWL_ONLY'} else len(enabled.split(','))
            execution,records=audit_execution(repo,directory,request,count,expected_java)
        for case in cases:
            recorded=by_id[case['id']]
            assert recorded['pass'] is True and recorded['group']==str(directory.relative_to(repo)), case['id']
            # A completed OWL check does not approve later numeric evaluation.
            # The pipeline status is authoritative; the OWL status is audited
            # independently when its expectation is defined by the fixture.
            effective_status=execution['status']
            assert recorded['execution_status']==effective_status
            if 'expected_status' in case:
                assert effective_status==case['expected_status'], case['id']
            else:
                assert execution['status']=='CONSISTENT'
                assert type(case['expected_boolean']) is bool
                assert read(directory/(case['id']+'.json'))['boolean'] is case['expected_boolean'], case['id']
            for key,output_key,record_key in [('expected_cleaning_status','cleaning_evaluation','cleaning_status'),('expected_context_status','context_evaluation','context_status'),('expected_numeric_status','numeric_evaluation','numeric_status')]:
                actual=execution.get(output_key,execution.get('validation',{}).get(output_key,{})).get('status')
                assert recorded[record_key]==actual and recorded.get(key)==case.get(key), case['id']
                if key in case:
                    assert actual==case[key], case['id']
            owl_status=execution.get('owl_validation_status',execution.get('validation',{}).get('owl_validation_status'))
            assert recorded.get('owl_status')==owl_status and recorded.get('expected_owl_status')==case.get('expected_owl_status'), case['id']
            if 'expected_owl_status' in case:
                assert owl_status==case['expected_owl_status'], case['id']
            assert recorded.get('failure_kind')==execution.get('failure_kind') and recorded.get('expected_failure_kind')==case.get('expected_failure_kind'), case['id']
            if 'expected_failure_kind' in case:
                assert execution.get('failure_kind')==case['expected_failure_kind'], case['id']
        evidence.append({'kind':'fixture_group','id':directory.name,'cases':[case['id'] for case in cases],
                         'query_ids':[query['id'] for query in request['queries']],'enabled_rules':request['enabled_rules'],'raw_files':records})
    return evidence


def audit(repo,evaluation=None):
    out=evaluation_dir(evaluation,repo=repo)
    assert not (out/'.evaluation-lock').exists(), 'Selected evaluation still has an active or stale writer lock'
    catalog=read(repo/'research/catalog.json')
    functional=read(out/'functional.json')
    functional_evidence=audit_functional(repo,catalog,functional,out)
    source_before=read(out/'benchmark-input-hashes.json')
    binary_before=read(out/'benchmark-runtime-hashes.json')
    assert source_before==read(out/'benchmark-input-hashes-after.json')==functional['input_hashes']==read(out/'artifacts.json')==live_source_hashes(repo)
    assert binary_before==read(out/'benchmark-runtime-hashes-after.json')==functional['runtime_hashes']==read(out/'runtime-binaries.json')==live_runtime_hashes(repo)
    benchmark_environment=out/'benchmark-environment.json'
    environment=read(benchmark_environment if benchmark_environment.exists() else out/'environment.json')
    versions={'OWLAPI-validator':'5.1.20','HermiT':'1.4.5.519','Jena':'4.10.0','SWRLAPI':'2.1.3','SWRLAPI-Drools-Engine':'2.1.3','Drools':'7.74.1.Final','OWLAPI-worker':'4.5.27'}
    assert environment['versions']==versions and environment['headless'] is True
    assert environment['jvm_heap_limit']=='2g parent +2g worker'
    expected_java=environment_java(environment)
    assert nonnegative_integer(environment['cpu_count']) and environment['cpu_count']>0
    fields=['timestamp_utc','platform','machine','cpu_count','python_version','maven_version']
    if environment['platform'].startswith('macOS'):fields+=['hw.model','hw.memsize','machdep.cpu.brand_string']
    for field in fields:
        assert environment.get(field), field
    configs=[c for c in catalog['configurations'] if c.get('benchmark')]
    assert len(configs)==4 and len({c['id'] for c in configs})==4
    ids={c['id'] for c in configs}
    order=read(out/'replicate-order.json')
    assert order['seed']==20261003 and order['new_process_each_run'] is True
    independently_shuffled=[[i,c['id']] for i in range(30) for c in configs]
    random.Random(20261003).shuffle(independently_shuffled)
    assert order['pairs']==independently_shuffled
    runs=[json.loads(line) for line in (out/'runs.jsonl').read_text().splitlines() if line]
    assert len(runs)==120
    expected_directories={f"{number:03d}-{run['configuration']}-r{run['replica']:02d}" for number,run in enumerate(runs,1)}
    assert {path.name for path in (out/'runs').iterdir() if path.is_dir()}==expected_directories, 'Run directory contains missing or stale replicates'
    assert collections.Counter(r['configuration'] for r in runs)=={c:30 for c in ids}
    assert collections.Counter((r['replica'],r['configuration']) for r in runs)=={(i,c):1 for i in range(30) for c in ids}
    summary=read(out/'summary.json')
    assert summary['replicates']==120 and summary['dataset_seed']==42 and summary['order_seed']==20261003
    assert all(nonnegative_integer(summary[key]) for key in ['replicates','dataset_seed','order_seed'])
    rows=summary['descriptive_statistics']
    assert collections.Counter(row['configuration'] for row in rows)=={c:1 for c in ids}
    summaries={row['configuration']:row for row in rows}
    evidence=[]
    for number,run in enumerate(runs,1):
        assert nonnegative_integer(run['order']) and nonnegative_integer(run['replica'])
        assert run['order']==number and [run['replica'],run['configuration']]==order['pairs'][number-1]
        cfg=next(c for c in configs if c['id']==run['configuration'])
        directory=out/'runs'/f"{number:03d}-{run['configuration']}-r{run['replica']:02d}"
        execution,records=audit_execution(repo,directory,expected_request(catalog,cfg,True),len(cfg['enabled_rule_ids']),expected_java)
        assert execution['numeric_evaluation']['status']==('VALID' if cfg['numeric_controls_enabled'] else 'NOT_APPLICABLE'), (directory,'numeric benchmark expectation')
        assert execution=={k:v for k,v in run.items() if k not in ['configuration','replica','order']}
        evidence.append({'order':number,'configuration':run['configuration'],'replica':run['replica'],'query_count':len(run['queries']),'raw_files':records})
    recomputed=[]
    for cfg in configs:
        sample=[r for r in runs if r['configuration']==cfg['id']]
        assert summaries[cfg['id']]['n']==30
        for metric in CORE_METRICS+POST_METRICS+['worker_'+x for x in WORKER_METRICS]:
            worker=metric.startswith('worker_')
            values=[r.get('swrl',{}).get(metric[7:],0) if worker else r.get(metric,0) for r in sample]
            stats={'mean':statistics.mean(values),'sd':statistics.stdev(values),'median':statistics.median(values),'min':min(values),'max':max(values)}
            for key,value in stats.items():
                reported=summaries[cfg['id']][metric][key]
                assert finite_nonnegative(reported) and math.isclose(value,reported,rel_tol=1e-12,abs_tol=1e-9), (cfg['id'],metric,key)
            applicable=sum(metric[7:] in r.get('swrl',{}) if worker else metric in r for r in sample)
            recomputed.append({'configuration':cfg['id'],'metric':metric,'applicable_replicates':applicable,'computed':stats})
    with (out/'summary.csv').open(newline='') as stream:
        csv_rows=list(csv.DictReader(stream))
    assert collections.Counter(x['configuration'] for x in csv_rows)=={c:1 for c in ids}
    for row in csv_rows:
        reported=summaries[row['configuration']]
        assert int(row['n'])==30
        for field,metric,statistic in [('mean_ms','total_ms','mean'),('sd_ms','total_ms','sd'),('median_ms','total_ms','median'),('mean_rss_bytes','observed_peak_process_tree_rss_bytes','mean')]:
            assert math.isclose(float(row[field]),reported[metric][statistic],rel_tol=1e-12,abs_tol=1e-9)
    global_files=['functional.json','environment.json','artifacts.json','runtime-binaries.json','runs.jsonl','summary.json','summary.csv','replicate-order.json','benchmark-input-hashes.json','benchmark-input-hashes-after.json','benchmark-runtime-hashes.json','benchmark-runtime-hashes-after.json']
    if benchmark_environment.exists():global_files.append('benchmark-environment.json')
    return {'status':'PASS','method':'Independent conserved-file, request, hash, multiset and arithmetic audit; no new model execution.',
            'replicates':120,'replicates_by_configuration':{c:30 for c in sorted(ids)},
            'functional_assertions':len(functional['assertions']),'golden_checks':105,
            'source_and_binary_hashes_before_after_match':True,'current_source_files_match':True,'current_runtime_binaries_match':True,
            'independent_order_reconstruction':True,'functional_raw_results_recotejado':True,
            'global_evidence_hashes':{relative:digest(out/relative) for relative in global_files},
            'verifier_script_sha256':digest(Path(__file__)),
            'statistics':recomputed,'replicate_evidence':evidence,'functional_evidence':functional_evidence,
            'limits':'No industrial observations or speed superiority. Baselines execute21queries; integrated executes21queries plus4ASK. Zero summaries for absent phases mean not applicable. Environment versions are recorded, not a JDK binary hash.'}


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--repo',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--evaluation-dir',help='Raw evaluation directory relative to --repo; or PROTYS_EVALUATION_OUT (default: current)')
    args=parser.parse_args()
    selected=evaluation_dir(args.evaluation_dir,repo=args.repo.resolve())
    require_safe_output(args.repo.resolve(),args.output,selected)
    assert not args.output.exists(), 'Preserve previous audit: '+str(args.output)
    report=audit(args.repo.resolve(),selected)
    assert not args.output.exists(), 'Preserve previous audit: '+str(args.output)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('x') as stream:
        stream.write(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':report['status'],'replicates':report['replicates']}))

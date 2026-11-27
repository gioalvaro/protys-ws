"""Approved snapshot numeric contracts; SPARQL preparation is not OWL inference.

Numeric equivalence compares values, never RDF-term COUNT. One selected factor
IRI is required. MIN collapses lexical duplicates only after uniqueness checks;
SUM operates on operation identities, never DISTINCT amounts across operations.
"""

def single_numeric(subject, predicate, value, tag, nonnegative=False, positive=False):
 guard=' && '+value+(' > 0' if positive else ' >= 0') if nonnegative or positive else ''
 return f'''{subject} {predicate} {value} .
FILTER(isNumeric({value}){guard})
FILTER NOT EXISTS {{ {subject} {predicate} ?{tag}Other .
FILTER(!isNumeric(?{tag}Other) || ?{tag}Other != {value}) }}'''

def valid_input(op='?op',tag='n',energy='?energy',factor='?factor',value='?factorValue'):
 return single_numeric(op,'i:consumesEnergyKWh',energy,tag+'Energy',nonnegative=True)+f'''
{op} t:hasEmissionFactor {factor} .
FILTER(isIRI({factor}))
FILTER NOT EXISTS {{ {op} t:hasEmissionFactor ?{tag}OtherFactor .
FILTER(!sameTerm(?{tag}OtherFactor,{factor})) }}
{factor} a t:ElectricityEmissionFactor ; t:factorUnit "kg CO2e/kWh" .
FILTER NOT EXISTS {{ {factor} t:factorUnit ?{tag}OtherUnit .
FILTER(STR(?{tag}OtherUnit) != "kg CO2e/kWh") }}
'''+single_numeric(factor,'t:factorValue',value,tag+'Factor',nonnegative=True)

def valid_output(op='?op',tag='n',energy='?energy',ghg='?ghg'):
 return valid_input(op,tag,energy,'?'+tag+'Factor','?'+tag+'FactorValue')+'\n'+single_numeric(op,'e:hasGHGImpactKgCO2e',ghg,tag+'GHG',nonnegative=True)+f'\nFILTER(ABS({ghg} - {energy} * ?{tag}FactorValue) <= 0.000000001)'

def scope(lot='?lot',process='?process',op='?op'):
 return f'{lot} a i:Lot ; i:hasProcess {process} ; i:hasActivity {op} .\n{op} a i:ProcessOperation ; i:partOfProcess {process} .'

def complete_lot(lot='?lot',tag='all'):
 return f'''FILTER NOT EXISTS {{ {scope(lot,'?'+tag+'Process','?'+tag+'Operation')}
FILTER NOT EXISTS {{ {valid_output('?'+tag+'Operation',tag+'Check','?'+tag+'Energy','?'+tag+'GHG')} }} }}'''

INPUT_INVALID='ASK { '+scope()+'\nFILTER NOT EXISTS { '+valid_input()+' } }'
OUTPUT_INVALID='ASK { '+scope()+'\nFILTER NOT EXISTS { '+valid_output()+' } }'
APPLICABLE='ASK { '+scope()+' }'
AMBIGUOUS='''ASK {
'''+scope()+'''
{ ?op i:consumesEnergyKWh ?left, ?right .
FILTER(isNumeric(?left) && isNumeric(?right) && ?left != ?right) }
UNION { ?op t:hasEmissionFactor ?leftFactor, ?rightFactor .
FILTER(!sameTerm(?leftFactor,?rightFactor)) }
UNION { ?op t:hasEmissionFactor ?factor . ?factor t:factorValue ?left, ?right .
FILTER(isNumeric(?left) && isNumeric(?right) && ?left != ?right) }
UNION { ?op e:hasGHGImpactKgCO2e ?left, ?right .
FILTER(isNumeric(?left) && isNumeric(?right) && ?left != ?right) }
}'''
# Reject preasserted conflicting emissions before producing the input marker.
PREPARE='''CONSTRUCT { ?op a t:NumericInputValid . } WHERE {
?op a i:ProcessOperation .
'''+valid_input()+'''
FILTER NOT EXISTS { ?op e:hasGHGImpactKgCO2e ?existing .
FILTER(!isNumeric(?existing) || ABS(?existing - ?energy * ?factorValue)>0.000000001) }
}'''

CLEAN_PAIR='''?lotA i:requiresCleaningBetween ?lotB ; i:processedOn ?equipment .
?lotB i:processedOn ?equipment .
'''+single_numeric('?lotA','t:endTimeSeconds','?endA','pairEnd')+'\n'+single_numeric('?lotB','t:startTimeSeconds','?startB','pairStart')+'\nFILTER(?endA <= ?startB)'
CLEAN_RECORD='''?clean a i:CleaningOperation ; t:cleanedResource ?equipment .
'''+single_numeric('?clean','t:startTimeSeconds','?cleanStart','cleanStart')+'\n'+single_numeric('?clean','t:endTimeSeconds','?cleanEnd','cleanEnd')+'''
FILTER(?cleanStart >= ?endA && ?cleanEnd <= ?startB && ?cleanEnd >= ?cleanStart)'''
C26='''ASK { ?lotA i:requiresCleaningBetween ?lotB .
FILTER NOT EXISTS { '''+CLEAN_PAIR+''' } }'''
R26='ASK { '+CLEAN_PAIR+'\nFILTER NOT EXISTS { '+CLEAN_RECORD+' } }'

"""AP242-derived local pilot: contextual same-IRI role classification."""
import json
from pathlib import Path
from lxml import etree as E
from build_rules import NS,q,uri,C,O,append_list
ROOT=Path(__file__).resolve().parents[2]
NS.update({'ap':'http://w3id.org/protys/ontology/iso10303#','prod':'http://w3id.org/protys/ontology/product#'})
RULES=[
 ('R27','Registro del perfil piloto representa un producto',[C('ap:Product_Definition','?x'),C('t:PilotProductRecord','?x')],[C('c:Product','?x')]),
 ('R28','Registro marcado representa un componente del ensamblaje piloto',[C('ap:Configuration_Item','?x'),C('t:PilotComponentRecord','?x'),O('t:componentOf','?x','?assembly'),C('t:PilotProductRecord','?assembly')],[C('prod:Production_Component','?x')]),
 ('R29','Representación geométrica del producto del perfil piloto',[C('ap:Shape_Representation','?x'),O('ap:hasShapeRepresentation','?assembly','?x'),C('t:PilotProductRecord','?assembly')],[C('prod:Product_Information','?x')]),
]
def main():
 path=ROOT/'ontologies/iso10303-alignment-rules.owl';tree=E.parse(str(path));root=tree.getroot()
 for n in root.xpath('./rdf:Description[swrl:body or rdf:type/@rdf:resource="'+NS['swrl']+'Variable"]',namespaces=NS):root.remove(n)
 for ident,title,body,head in RULES:
  n=E.SubElement(root,q('rdf','Description'));n.set(q('rdf','about'),NS['a']+ident)
  E.SubElement(n,q('rdf','type')).set(q('rdf','resource'),NS['swrl']+'Imp');E.SubElement(n,q('rdfs','label')).text=ident+': '+title
  for side,atoms in [('body',body),('head',head)]:append_list(E.SubElement(n,q('swrl',side)),atoms)
 for var in ['?x','?assembly']:
  n=E.SubElement(root,q('rdf','Description'));n.set(q('rdf','about'),uri(var));E.SubElement(n,q('rdf','type')).set(q('rdf','resource'),NS['swrl']+'Variable')
 tree.write(str(path),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 path=ROOT/'ontologies/iso10303-ap242-fragment.owl';tree=E.parse(str(path));root=tree.getroot()
 for name in ['PilotProductRecord','PilotComponentRecord']:
  if not root.xpath('./owl:Class[@rdf:about="'+NS['t']+name+'"]',namespaces=NS):
   n=E.SubElement(root,q('owl','Class'));n.set(q('rdf','about'),NS['t']+name);E.SubElement(n,q('rdfs','comment')).text='Explicit selection marker of the local operational pilot; no universal identity or subclass mapping from AP242 information concepts to physical products/components.'
 for n in root.xpath('./owl:Ontology/rdfs:comment',namespaces=NS):n.text='Local, partial ontology derived from selected AP242 concepts. Configuration_Item represents a configuration concept and Product_Definition an information view; three contextual pilot rules assign PROTYS roles only to explicitly marked records. Same IRI and source type are retained. No full STEP/EXPRESS conformance or universal class equivalence is claimed.'
 tree.write(str(path),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 def atom(a):return a[1]+'('+', '.join(a[2])+')'
 contract={'scope':'47 explicitly marked component records and their marked assembly in a local operational pilot; accumulating PROTYS role types on existing IRIs, not creating physical objects or universally classifying AP242 records','sources':['https://www.steptools.com/stds/stp_aim/html/t_configuration_item.html','https://www.steptools.com/stds/stp_aim/html/t_product_definition.html'],'rules':[{'id':ident,'title':title,'code':' ^ '.join(atom(a) for a in body)+' → '+' ^ '.join(atom(a) for a in head)} for ident,title,body,head in RULES],'negative_cases':['Configuration_Item without PilotComponentRecord','Product_Definition without PilotProductRecord','Shape_Representation not linked to a marked product record'],'identity_policy':'Same source IRI and AP242 type remain; whole named individual sets are compared before/after materialization'}
 (ROOT/'research/model/h3-extension-contract.json').write_text(json.dumps(contract,ensure_ascii=False,indent=2)+'\n');print('Three H3 rules scoped to explicit pilot context')
if __name__=='__main__':main()

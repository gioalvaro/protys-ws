package org.protys.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;

/** Parser-only checks. A successful parse says nothing about model consistency. */
public class SyntaxCheck {
  public static void main(String[] args) throws Exception {
    List<Map<String,Object>> checks=new ArrayList<>();
    boolean passed=true;
    for(String argument:args){
      Path path=Path.of(argument).toAbsolutePath();
      Map<String,Object> row=new LinkedHashMap<>();row.put("path",path.toString());
      try {
        row.put("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        if(path.toString().endsWith(".sparql")||path.toString().endsWith(".rq")) {
          var query=QueryFactory.create(Files.readString(path));row.put("kind","SPARQL");row.put("query_type",query.getQueryType());
        } else {
          var model=ModelFactory.createDefaultModel();RDFDataMgr.read(model,path.toUri().toString());row.put("kind","RDF");row.put("triples",model.size());model.close();
        }
        row.put("status","PASS");
      } catch(Exception error){passed=false;row.put("status","FAIL");row.put("error",error.toString());}
      checks.add(row);
    }
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out,Map.of("status",passed?"PASS":"FAIL","scope","Syntax only; no OWL profile, consistency or SWRL execution assessed.","checks",checks));
    System.exit(passed?0:1);
  }
}

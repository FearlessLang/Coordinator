package docBuilder;

import static offensiveUtils.Require.*;

import java.util.*;
import java.util.stream.Collectors;

import core.E.Literal;

public final class DocNames{
  private DocNames(){}

  static final List<String> baseNames= List.of(  
"Action",
"Angle", 
"Bool", 
"Byte",
"Count", 
"DataType",  
"DataTypeBy",  
"Degree",
"EList",
"F",
"Float",
"Flow",
"Info",
"Int",
"IsoPod",  
"KeyElem", 
"LazyInfo", 
"List",
"MF",
"Main",  
"Map",
"Nat",
"Num",
"Opt",
"Order",
"OrderBy",
"OrderHash",
"OrderHashBy",
"Radian",
"Repr",
"Sealed",
"CaptureFree",
"Slot",
"StackFrame",  
"Str",
"StrBy",  
"System", 
"Test",
"Tests", 
"ToImm",
"ToInfo", 
"ToInfoBy",  
"ToIso",
"ToStr",
"ToStrBy",  
"ToUStr",
"UStr",
"Var",
"Void", 
"WidenTo"
);

  public static Map<String,String> uses(String pkgName, List<Literal> core){
    assert nonNull(pkgName,core);
    var local= core.stream()
      .filter(l->l.name().pkgName().equals(pkgName))
      .map(l->l.name().simpleName())
      .collect(Collectors.toSet());
    var res= new LinkedHashMap<String,String>();
    baseNames.stream()
      .filter(n->!local.contains(n))
      .forEach(n->res.put("base."+n,n));
    return Collections.unmodifiableMap(res);
  }
}
package docBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.TName;

public final class DocNames{
  static final List<String> baseNames= List.of(
    "Action",
    "Angle",
    "BaseContainer",
    "BaseId",
    "Bool",
    "Byte",
    "CaptureFree",
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
    "StackFrame",
    "Str",
    "System",
    "Test",
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
  public static Map<String,String> uses(Stream<TName> types, Function<TName,String> names){
    var taken= types
      .filter(n -> !n.s().equals("base."+names.apply(n)))
      .map(names)
      .collect(Collectors.toSet());
    var res= new LinkedHashMap<String,String>();
    baseNames.stream()
      .filter(n -> !taken.contains(n))
      .forEach(n -> res.put("base."+n, n));
    return Collections.unmodifiableMap(res);
  }
}

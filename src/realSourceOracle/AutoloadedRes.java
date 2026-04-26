package realSourceOracle;

import static offensiveUtils.Require.*;
import java.util.List;

public record AutoloadedRes(String text, List<String> declaredTypes){
  public AutoloadedRes{
    assert nonNull(text, declaredTypes);
    assert unmodifiable(declaredTypes, "Autoloaded.declaredTypes");
    assert text.isEmpty() == declaredTypes.isEmpty();
  }
  public static AutoloadedRes none(){ return new AutoloadedRes("", List.of()); }
}
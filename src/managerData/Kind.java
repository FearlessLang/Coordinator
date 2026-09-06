package managerData;

public enum Kind{
  idle, code, dataReadOnly, dataReadWrite;
  public String infoText(){ return switch(this){
    case idle -> "idle";
    case code -> "code";
    case dataReadOnly -> "data:readOnly";
    case dataReadWrite -> "data:readWrite";
  };}
  public boolean isData(){ return this == dataReadOnly || this == dataReadWrite; }
  public static java.util.Optional<Kind> of(String text){
    return java.util.Arrays.stream(values()).filter(k->k.infoText().equals(text)).findFirst();
  }
}

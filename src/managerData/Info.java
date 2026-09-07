package managerData;

import java.util.List;
import java.util.Optional;

import metaParser.Span;

public sealed interface Info{
  Span span();
  record Str(String value, Span span) implements Info{}
  record Lst(List<Info> items, Span span) implements Info{}
  record Obj(List<Field> fields, Span span) implements Info{
    public record Field(String key, Span keySpan, Info value){}
    public Optional<Info> get(String key){ return fields.stream().filter(f->f.key().equals(key)).map(Field::value).findFirst(); }
    public Optional<Field> field(String key){ return fields.stream().filter(f->f.key().equals(key)).findFirst(); }
  }
  default boolean isEmpty(){ return switch(this){
    case Str s -> s.value().isEmpty();
    case Lst l -> l.items().isEmpty();
    case Obj o -> o.fields().isEmpty();
  };}
}

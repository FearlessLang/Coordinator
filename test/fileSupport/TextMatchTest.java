package fileSupport;

import static fileSupport.ByteFiles.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class TextMatchTest{
  @Test void theWindowsTextOfError14IsAMemoryShortage(){
    assertEquals(ResourceExhausted_Memory, TextMatch.match(" not enough memory resources are available to complete this operation"));
  }
  @Test void theWindowsTextOfError1167IsADisconnectedDevice(){
    assertEquals(StorageUnavailable_DeviceDisconnected, TextMatch.match(" the device is not connected"));
  }
  @Test void theWindowsTextOfError1450IsAShortageOfSystemResources(){
    assertEquals(ResourceExhausted_SystemResources, TextMatch.match(" insufficient system resources exist to complete the requested service"));
  }
}

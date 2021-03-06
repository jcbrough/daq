package daq.usi.ovs;

import daq.usi.ResponseHandler;
import daq.usi.SwitchController;
import grpc.InterfaceResponse;
import grpc.LinkStatus;
import grpc.POEStatus;
import grpc.POESupport;
import grpc.PowerResponse;
import grpc.SwitchActionResponse;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenVSwitch implements SwitchController {

  private static final String OVS_OUTPUT_FILE = "ovs_output.txt";

  protected String getInterfaceByPort(int devicePort) throws FileNotFoundException {
    URL file = OpenVSwitch.class.getClassLoader().getResource(OVS_OUTPUT_FILE);
    if (file == null) {
      throw new FileNotFoundException(OVS_OUTPUT_FILE + " is not found!");
    }
    FileReader reader = new FileReader(file.getFile());
    BufferedReader bufferedReader = new BufferedReader(reader);
    Pattern pattern = Pattern.compile("(^\\s*" + devicePort + ")(\\((.+)\\))(:.*)", 'g');
    String interfaceLine = bufferedReader.lines().filter(line -> {
      Matcher m = pattern.matcher(line);
      return m.find();
    }).findFirst().get();

    Matcher m = pattern.matcher(interfaceLine);
    m.matches();
    return m.group(3);
  }

  @Override
  public void getPower(int devicePort, ResponseHandler<PowerResponse> handler) throws Exception {
    PowerResponse.Builder response = PowerResponse.newBuilder();
    PowerResponse power = response.setPoeStatus(POEStatus.OFF)
        .setPoeSupport(POESupport.DISABLED)
        .setMaxPowerConsumption(0)
        .setCurrentPowerConsumption(0).build();
    handler.receiveData(power);
  }

  @Override
  public void getInterface(int devicePort, ResponseHandler<InterfaceResponse> handler)
      throws Exception {
    InterfaceResponse.Builder response = InterfaceResponse.newBuilder();
    InterfaceResponse iface = response.setLinkStatus(LinkStatus.UP)
        .setDuplex("")
        .setLinkSpeed(0)
        .build();
    handler.receiveData(iface);
  }

  private void managePort(int devicePort, ResponseHandler<SwitchActionResponse> handler,
                          boolean enabled) throws Exception {
    String iface = getInterfaceByPort(devicePort);
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.command("bash", "-c", "ifconfig " + iface + (enabled ? " up" : " down"));
    Process process = processBuilder.start();
    int exitCode = process.waitFor();
    handler.receiveData(SwitchActionResponse.newBuilder().setSuccess(exitCode == 0).build());
  }

  @Override
  public void connect(int devicePort, ResponseHandler<SwitchActionResponse> handler)
      throws Exception {
    managePort(devicePort, handler, true);
  }

  @Override
  public void disconnect(int devicePort, ResponseHandler<SwitchActionResponse> handler)
      throws Exception {
    managePort(devicePort, handler, false);
  }

  public void start() {
  }
}

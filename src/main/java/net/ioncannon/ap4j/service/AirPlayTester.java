package net.ioncannon.ap4j.service;

import net.ioncannon.ap4j.model.Device;

/**
 * Created by jerry on 9/19/14.
 */
public class AirPlayTester implements DeviceConnectionService.DeviceStatusAware {

    public static void main(String[] args) throws Exception {
        DeviceConnectionService.registerDeviceListener(new AirPlayTester());

        while (true) {
            Thread.sleep(1000L);
        }

    }

    @Override
    public void deviceConnected(Device d) {
        final Device fd = d;
        new Thread(){ public void run() {
            DeviceConnectionService.ServiceResponse resp = DeviceConnectionService.sendStartStreamCmd(fd, "https://r12---sn-a5m7ln7d.googlevideo.com/videoplayback?mt=1411114008&initcwndbps=11537500&id=o-AKPPUFSgaTqg02I3Nlws9Wki-JbUv6JpddTHR0RtCt7F&mv=m&source=youtube&ms=au&signature=79C3CD0D97E0C76519C79FEE56609882CF123C07.7D4D603E07F30B1DB1249A976B04E42CCCF5B281&requiressl=yes&mm=31&ipbits=0&sver=3&sparams=id%2Cinitcwndbps%2Cip%2Cipbits%2Citag%2Cmm%2Cms%2Cmv%2Cratebypass%2Crequiressl%2Csource%2Cupn%2Cexpire&itag=22&key=yt5&expire=1411135679&ratebypass=yes&ip=23.89.137.202&fexp=927622%2C927882%2C930666%2C931983%2C932404%2C934030%2C936117%2C945545%2C946023%2C947209%2C952302%2C953801&upn=DoonAGGDVio", 0.0);
            System.out.println("airplay start stream command response: " + resp.success + ": " + resp.errorMessage);
        }}.start();
    }

    @Override
    public void deviceDisconnected(Device d) {

    }
}

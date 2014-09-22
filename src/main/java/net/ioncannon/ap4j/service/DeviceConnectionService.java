package net.ioncannon.ap4j.service;

import net.ioncannon.ap4j.command.PlayCommand;
import net.ioncannon.ap4j.command.RateCommand;
import net.ioncannon.ap4j.command.ScrubCommand;
import net.ioncannon.ap4j.command.StopCommand;
import net.ioncannon.ap4j.model.DeviceConnection;
import net.ioncannon.ap4j.model.Device;
import net.ioncannon.ap4j.model.DeviceResponse;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceListener;
import javax.xml.ws.Service;

/**
 * Copyright (c) 2011 Carson McDonald
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

public class DeviceConnectionService {

    private static Logger logger = Logger.getLogger(DeviceConnectionService.class.getName());

    public static class ServiceResponse {
        public boolean success = true;
        public Map<String, String> dataMap = new HashMap<String, String>();
        public String errorMessage;
        public Exception errorException;


        static ServiceResponse OK = new ServiceResponse();
    }

    public static interface DeviceStatusAware {
        public void deviceConnected(Device d);
        public void deviceDisconnected(Device d);
    }

    private static List<DeviceStatusAware> deviceListeners = new ArrayList<DeviceStatusAware>();
    private static Map<String, Device> deviceMap = new HashMap<String, Device>();
    private static Map<String, DeviceConnection> deviceConnectionMap = new HashMap<String, DeviceConnection>();
    private static ServiceListener airplayServiceListener = new AirPlayJmDNSServiceListener();

    private static JmDNS jmDNS = null;

    private static Thread jmDNSRunner = null;

    public static void registerDeviceListener(DeviceStatusAware dsa) {
        logger.info("add new device status listener: " + dsa);
        deviceListeners.add(dsa);

        if (jmDNSRunner == null) {
            jmDNSRunner = new Thread(new Runnable() {
                @Override
                public void run() {
                    try
                    {
                        jmDNS = JmDNS.create();
                        logger.info(Thread.currentThread() + ": created jmDNS service " + jmDNS);
                        jmDNS.addServiceListener("_airplay._tcp.local.", airplayServiceListener);
                        logger.info(Thread.currentThread() + ": started jmDNS service " + jmDNS);
                    }
                    catch (Exception e)
                    {
                        logger.log(Level.SEVERE, e.getMessage(), e);
                    }

                    logger.info(Thread.currentThread() + ": reach end of life with jmDNS service " + jmDNS);
                }
            });
            jmDNSRunner.start();
        }

    }

    public static void unregisterDeviceListener(DeviceStatusAware dsa) {
        deviceListeners.remove(dsa);

        if (deviceListeners.isEmpty() && jmDNS != null) {
            final JmDNS tdJmDNS = jmDNS;

            jmDNS = null;
            jmDNSRunner = null;

            new Thread() {
                public void run() {
                    try {
                        tdJmDNS.removeServiceListener("_airplay._tcp.local.", airplayServiceListener);
                        tdJmDNS.close();
                        logger.info(Thread.currentThread() + ": closed jmDNS service " + tdJmDNS + ".");
                    } catch (Exception e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                }
            }.start();
        }
    }

    public static DeviceConnection getConnection(Device device) {
        if (!deviceConnectionMap.containsKey(device.getId())) {
            deviceConnectionMap.put(device.getId(), new DeviceConnection(device));
        }
        return deviceConnectionMap.get(device.getId());
    }

    public static void addDevice(Device device) {

        logger.info("Adding device: " + device);

        deviceMap.put(device.getId(), device);

        for (DeviceStatusAware dsa : deviceListeners) {
            dsa.deviceConnected(device);
        }
    }

    public static void removeDevice(String deviceId) {
        if (deviceMap.containsKey(deviceId)) {
            Device removedDevice = deviceMap.remove(deviceId);
            DeviceConnection deviceConnection = getConnection(removedDevice);
            if (deviceConnection != null)
            {
                deviceConnection.close();
            }

            for (DeviceStatusAware dsa : deviceListeners) {
                dsa.deviceDisconnected(removedDevice);
            }

        }
    }


    /**
     *
     * @param device
     * @param streamURL
     * @return
     */
    public static ServiceResponse sendStartStreamCmd(Device device, String streamURL, double pos) {

        try {
            DeviceConnection deviceConnection = getConnection(device);

            DeviceResponse deviceResponse = deviceConnection.sendCommand(new PlayCommand(streamURL, pos));

            if (deviceResponse.getResponseCode() == 200) {
                return ServiceResponse.OK;
            } else {
                ServiceResponse srep = new ServiceResponse();
                srep.success = false;
                srep.errorMessage = deviceResponse.getResponseMessage();
                return srep;
            }
        } catch (Exception e) {
            ServiceResponse srep = new ServiceResponse();
            srep.success = false;
            srep.errorMessage = e.getMessage();
            srep.errorException = e;
            return srep;
        }
    }

    public static ServiceResponse sendStopStreamCmd(Device device) {

        try {
            DeviceConnection deviceConnection = getConnection(device);

            DeviceResponse deviceResponse = deviceConnection.sendCommand(new StopCommand());

            if (deviceResponse.getResponseCode() == 200) {
                return ServiceResponse.OK;
            } else {
                ServiceResponse srep = new ServiceResponse();
                srep.success = false;
                srep.errorMessage = deviceResponse.getResponseMessage();
                return srep;
            }
        } catch (Exception e) {
            ServiceResponse srep = new ServiceResponse();
            srep.success = false;
            srep.errorMessage = e.getMessage();
            srep.errorException = e;
            return srep;
        }
    }

    public static ServiceResponse sendScrubCmd(Device device, double pos) {

        try {
            DeviceConnection deviceConnection = getConnection(device);

            DeviceResponse deviceResponse = deviceConnection.sendCommand(pos<=0?new ScrubCommand():new ScrubCommand(pos));

            if (deviceResponse.getResponseCode() == 200) {
                ServiceResponse srep = new ServiceResponse();
                srep.success = true;
                srep.dataMap.put("duration", deviceResponse.getContentParameterMap().get("duration"));
                srep.dataMap.put("position", deviceResponse.getContentParameterMap().get("position"));
                return srep;
            } else {
                ServiceResponse srep = new ServiceResponse();
                srep.success = false;
                srep.errorMessage = deviceResponse.getResponseMessage();
                return srep;
            }
        } catch (Exception e) {
            ServiceResponse srep = new ServiceResponse();
            srep.success = false;
            srep.errorMessage = e.getMessage();
            srep.errorException = e;
            return srep;
        }
    }

    public static ServiceResponse sendRateCmd(Device device, double rate) {
        try {
            DeviceConnection deviceConnection = getConnection(device);

            DeviceResponse deviceResponse = deviceConnection.sendCommand(new RateCommand(rate));

            if (deviceResponse.getResponseCode() == 200) {
                return ServiceResponse.OK;
            } else {
                ServiceResponse srep = new ServiceResponse();
                srep.success = false;
                srep.errorMessage = deviceResponse.getResponseMessage();
                return srep;
            }
        } catch (Exception e) {
            ServiceResponse srep = new ServiceResponse();
            srep.success = false;
            srep.errorMessage = e.getMessage();
            srep.errorException = e;
            return srep;
        }
    }
}

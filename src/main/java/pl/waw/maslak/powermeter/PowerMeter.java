package pl.waw.maslak.powermeter;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
//import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class PowerMeter {

    public static String host = "test.mosquitto.org";
    public static String port = "1883";
    public static String client_id = "power-meter";
    public static String topic_prefix = "";
    public static String username;
    public static String password;

    public static boolean connect = false;
    public static boolean verbose = false;

    public static long startTime = System.currentTimeMillis();
    public static long stopTime = System.currentTimeMillis();
    public static boolean first = true;
    public static String power = "0";
    public static double energy = 0;
    public static int timeout = 60; // seconds
    public static int interval = 1; // seconds
    public static int pulses = 1000;

    public static MqttClient sampleClient;
    //public static MqttMessage message;

    public static MqttClient powerClient;

    public static void main(String[] args) throws InterruptedException {

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equalsIgnoreCase("-host")) {
                String value = args[++i];
                host = value;
                connect = true;
            }
            if (arg.equalsIgnoreCase("-port")) {
                String value = args[++i];
                port = value;
                connect = true;
            }
            if (arg.equalsIgnoreCase("-client_id")) {
                String value = args[++i];
                client_id = value;
                connect = true;
            }
            if (arg.equalsIgnoreCase("-topic_prefix")) {
                String value = args[++i];
                topic_prefix = value;
                connect = true;
            }
            if (arg.equalsIgnoreCase("-username")) {
                String value = args[++i];
                username = value;
                connect = true;
            }
            if (arg.equalsIgnoreCase("-password")) {
                String value = args[++i];
                password = value;
                connect = true;
            }
            if (arg.equalsIgnoreCase("-timeout")) {
                String value = args[++i];
                timeout = Integer.parseInt(value);
                connect = true;
            }
            if (arg.equalsIgnoreCase("-interval")) {
                String value = args[++i];
                interval = Integer.parseInt(value);
                connect = true;
            }
            if (arg.equalsIgnoreCase("-pulses")) {
                String value = args[++i];
                pulses = Integer.parseInt(value);
            }
            if (arg.equalsIgnoreCase("-verbose")) {
                verbose = true;
            }
        }

        // GPIO Listener
        final GpioController gpio = GpioFactory.getInstance();
        final GpioPinDigitalInput gpio1 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_00, PinPullResistance.PULL_DOWN);
        gpio1.setShutdownOptions(true);
        gpio1.addListener(new GpioPinListenerDigital() {
            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                if (event.getState().isHigh()) {
                    if (startTime == stopTime && first == true) {
                        first = false;
                    } else {
                        startTime = stopTime;
                        stopTime = System.currentTimeMillis();
                        power = String.valueOf(3600 * pulses / (stopTime - startTime));
                    }
                    energy = energy + ((double) 1 / pulses);
                    if (verbose) {
                        System.out.println("power: " + power + ", energy: " + String.format("%.3f", energy));
                    }
                }
            }
        });
        // GPIO Listener

        // MQTT Connector
        if (connect) {
            try {
                powerClient = new MqttClient("tcp://" + host + ":" + port, client_id);
            } catch (MqttException ex) {
                Logger.getLogger(PowerMeter.class.getName()).log(Level.SEVERE, null, ex);
            }
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setConnectionTimeout(timeout);
            connOpts.setKeepAliveInterval(15);
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                connOpts.setUserName(username);
                connOpts.setPassword(password.toCharArray());
            }
            connOpts.setAutomaticReconnect(true);
            if (verbose) {
                System.out.println("power: " + power + ", energy: " + String.format("%.3f", energy));
            }

            try {
                powerClient.connect(connOpts);
            } catch (MqttException ex) {
                // System.out.println("before-while");
                Logger.getLogger(PowerMeter.class.getName()).log(Level.SEVERE, null, ex);
            }
            while (connect) {
                try {
                    powerClient.publish(topic_prefix + "energy", new MqttMessage(String.format("%.3f", energy).getBytes()));
                } catch (MqttException ex) {
                    Logger.getLogger(PowerMeter.class.getName()).log(Level.SEVERE, "The error was reported during energy publication", ex);
                    connect = false;
                    gpio.shutdown();
                }
                try {
                    powerClient.publish(topic_prefix + "power", new MqttMessage(power.getBytes()));
                } catch (MqttException ex) {
                    Logger.getLogger(PowerMeter.class.getName()).log(Level.SEVERE, "The error was reported during power publication", ex);
                    connect = false;
                    gpio.shutdown();
                }
                if (verbose) { System.out.print("."); }
                Thread.sleep(interval * 1000);
            }
        }
         // MQTT Connector
    }
}

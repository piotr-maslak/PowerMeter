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
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

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
                        power = String.valueOf(3600000 / (stopTime - startTime));
                    }
                    energy = energy + 0.001;
                    if (verbose) {
                        System.out.println("power: " + power + ", energy: " + String.format("%.3f", energy));
                    }
                }
            }
        });

        if (connect) {
            MemoryPersistence persistence = new MemoryPersistence();
            try {
                powerClient = new MqttClient("tcp://" + host + ":" + port, client_id, persistence);
                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);
                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    connOpts.setUserName(username);
                    connOpts.setPassword(password.toCharArray());
                }
                connOpts.setAutomaticReconnect(true);
                if (verbose) {
                    System.out.println("power: " + power + ", energy: " + String.format("%.3f", energy));
                }
                powerClient.connect(connOpts);
                while (true) {

                    powerClient.publish(topic_prefix + "power", new MqttMessage(power.getBytes()));
                    powerClient.publish(topic_prefix + "energy", new MqttMessage(String.format("%.3f", energy).getBytes()));

                    System.out.print(".");
                    Thread.sleep(1000);
                }
            } catch (MqttException ex) {
                Logger.getLogger(PowerMeter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }
}

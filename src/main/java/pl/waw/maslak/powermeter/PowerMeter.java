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

    public static String host = "iot.maslak.waw.pl";
    public static String client_id = "powermeter";
    public static String topic_prefix = "";
    public static String username = "maslak";
    public static String password = "maslak";

    public static long startTime = System.currentTimeMillis();
    public static long stopTime = System.currentTimeMillis();
    public static String power = "0";
    public static double energy = 0;
    public static MqttClient powerClient;

    public static void main(String[] args) throws InterruptedException {

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = args[++i];

            if (arg.equalsIgnoreCase("-host")) {
                host = value;
            }
            if (arg.equalsIgnoreCase("-client_id")) {
                client_id = value;
            }
            if (arg.equalsIgnoreCase("-topic_prefix")) {
                topic_prefix = value;
            }
            if (arg.equalsIgnoreCase("-username")) {
                username = value;
            }
            if (arg.equalsIgnoreCase("-password")) {
                password = value;
            }
        }

        MemoryPersistence persistence = new MemoryPersistence();

        try {
            powerClient = new MqttClient("tcp://" + host + ":1883", client_id, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setUserName(username);
            connOpts.setPassword(password.toCharArray());
            connOpts.setAutomaticReconnect(true);
            powerClient.connect(connOpts);
            powerClient.publish(topic_prefix + "power", new MqttMessage(power.getBytes()));
            powerClient.publish(topic_prefix + "energy", new MqttMessage(String.format("%.4f", energy).getBytes()));

        } catch (MqttException ex) {
            Logger.getLogger(PowerMeter.class.getName()).log(Level.SEVERE, null, ex);
        }

        // gpio controller
        final GpioController gpio = GpioFactory.getInstance();
        final GpioPinDigitalInput myButton1 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_00, PinPullResistance.PULL_DOWN);
        myButton1.setShutdownOptions(true);
        myButton1.addListener(new GpioPinListenerDigital() {
            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                if (event.getState().isHigh()) {
                    startTime = stopTime;
                    stopTime = System.currentTimeMillis();
                    power = String.valueOf(3600000 / (stopTime - startTime));
                    energy = energy + 0.0001;
                    try {
                        powerClient.publish(topic_prefix + "power", new MqttMessage(power.getBytes()));
                        powerClient.publish(topic_prefix + "energy", new MqttMessage(String.format("%.4f", energy).getBytes()));
                    } catch (MqttException ex) {
                        Logger.getLogger(PowerMeter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        });

        while (true) {
            Thread.sleep(1000);
        }
    }
}

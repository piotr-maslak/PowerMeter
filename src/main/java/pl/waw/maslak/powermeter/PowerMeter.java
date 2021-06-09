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

    public static long timestamp1 = System.currentTimeMillis();
    public static long timestamp2 = System.currentTimeMillis();
    public static String power = "0";
    public static double energy = 0;
    public static MqttClient sampleClient;
    //public static MqttMessage message;

    public static void main(String[] args) throws InterruptedException {

        String broker = "tcp://localhost:1883";
        String clientId = "PowerMeter";
        MemoryPersistence persistence = new MemoryPersistence();

        try {
            sampleClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            sampleClient.connect(connOpts);

            //message.setQos(2);
            sampleClient.publish("power", new MqttMessage(power.getBytes()));
            sampleClient.publish("energy", new MqttMessage(String.format("%.4f", energy).getBytes()));

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
                    timestamp1 = timestamp2;
                    timestamp2 = System.currentTimeMillis();
                    power = String.valueOf(3600000 / (timestamp2 - timestamp1));
                    energy = energy + 0.0001;
                    try {
                        sampleClient.publish("power", new MqttMessage(power.getBytes()));
                        sampleClient.publish("energy", new MqttMessage(String.format("%.4f", energy).getBytes()));
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

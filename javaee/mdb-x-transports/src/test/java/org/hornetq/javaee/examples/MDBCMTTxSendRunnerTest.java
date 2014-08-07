/*
* JBoss, Home of Professional Open Source.
* Copyright 2010, Red Hat, Inc., and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.hornetq.javaee.examples;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.hornetq.javaee.example.server.MDBMessageSendTxExample;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.metadata.ManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;


import static org.junit.Assert.assertTrue;

@RunAsClient
@RunWith(Arquillian.class)
public class MDBCMTTxSendRunnerTest {
    @Deployment
    public static Archive getDeployment() {

        final JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class, "mdb.jar");
        ejbJar.addClass(MDBMessageSendTxExample.class);  // Generate the manifest with it's dependencies
        ejbJar.setManifest(new Asset() {
            public InputStream openStream() {
                ManifestBuilder builder = ManifestBuilder.newInstance();
                StringBuffer dependencies = new StringBuffer();
                dependencies.append("org.jboss.as.naming");
                builder.addManifestHeader("Dependencies", dependencies.toString());
                return builder.openStream();
            }
        });
        System.out.println(ejbJar.toString(true));
        return ejbJar;
    }

    @Test
    public void testFromAmqpAndMqttViaMDBtoOpenWire() throws Exception {
        Connection openwire = createOpenwireConnection();
        Session oSession = openwire.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer messageConsumer = oSession.createConsumer(oSession.createQueue("replyQueue"));

        Connection amqp = createAmqpConnection();
        Session session = amqp.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(session.createTopic("topic://testTopic"));
        producer.send(session.createTextMessage("AMQP"));


        Message message = messageConsumer.receive(10000);
        assertTrue("is text", message instanceof TextMessage);

        String text = ((TextMessage) message).getText();
        System.out.println("got reply: " + text);
        assertTrue("from amqp", text.toLowerCase().contains("amqp"));

        amqp.close();


        final BlockingConnection mqtt = createMQTTConnection().blockingConnection();
        mqtt.connect();
        byte[] payload = bytes("MQTT");
        mqtt.publish("testTopic", payload, QoS.AT_LEAST_ONCE, false);
        mqtt.disconnect();

        message = messageConsumer.receive(10000);

        assertTrue("is text", message instanceof TextMessage);
        text = ((TextMessage) message).getText();
        System.out.println("got reply: " + text);
        assertTrue("from mqtt", text.toLowerCase().contains("mqtt"));

        openwire.close();

    }

    private byte[] bytes(String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected MQTT createMQTTConnection() throws Exception {
        MQTT mqtt = new MQTT();
        mqtt.setConnectAttemptsMax(1);
        mqtt.setReconnectAttemptsMax(0);
        mqtt.setHost("localhost", 61618);
        return mqtt;
    }

    public Connection createAmqpConnection() throws Exception {
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl("localhost", 61617, "admin", "password");
        final Connection connection = factory.createConnection();
        connection.setExceptionListener(new ExceptionListener() {
            @Override
            public void onException(JMSException exception) {
                exception.printStackTrace();
            }
        });
        connection.start();
        return connection;
    }

    public Connection createOpenwireConnection() throws Exception {
        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        final Connection connection = factory.createConnection();
        connection.setExceptionListener(new ExceptionListener() {
            @Override
            public void onException(JMSException exception) {
                exception.printStackTrace();
            }
        });
        connection.start();
        return connection;
    }

}

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

import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.security.AuthenticationUser;
import org.apache.activemq.security.SimpleAuthenticationBroker;
import org.apache.activemq.security.SimpleAuthenticationPlugin;
import org.slf4j.LoggerFactory;
import org.hornetq.javaee.example.MDBMessageSendTxClientExample;
import org.hornetq.javaee.example.server.MDBMessageSendTxExample;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
//import org.jboss.osgi.testing.ManifestBuilder;
import org.jboss.osgi.metadata.ManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         5/21/12
 */
@RunAsClient
@RunWith(Arquillian.class)
public class MDBCMTTxSendRunnerTest
{
   Logger LOG = LoggerFactory.getLogger(MDBCMTTxSendRunnerTest.class);
   @Deployment
   public static Archive getDeployment()
   {

      final JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class, "mdb.jar");
      ejbJar.addClass(MDBMessageSendTxExample.class);  // Generate the manifest with it's dependencies
     ejbJar.setManifest(new Asset()
     {
        public InputStream openStream()
        {
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
   public void runExample() throws Exception
   {
       final Vector<Exception> throwables = new Vector<Exception>();

       final AtomicBoolean done = new AtomicBoolean(false);
       Thread restartThread = new Thread() {

           @Override
           public void run() {

               int count=0;
               try {
                   while(!done.get()) {

                       LOG.info("Starting broker...");
                       BrokerService brokerService = new BrokerService();
                       brokerService.setDataDirectory("target");
                       brokerService.setBrokerName("willFailOver");
                       brokerService.addConnector("tcp://localhost:61616");
                       brokerService.setPlugins(new BrokerPlugin[]{new SimpleAuthenticationPlugin(Arrays.asList(new AuthenticationUser[]{new AuthenticationUser("guest","password","guest")}))});
                       brokerService.start();
                       brokerService.waitUntilStarted();

                       LOG.info("Started broker (" + (count++) + ") " + brokerService + ", addr:" + brokerService.getAdminView().getTransportConnectors());

                       while (!done.get() && brokerService.getAdminView().getTotalDequeueCount() < 200) {
                           TimeUnit.MILLISECONDS.sleep(200);
                       }
                       LOG.info("Stopping broker on dequeue count:" + brokerService.getAdminView().getTotalDequeueCount());
                       brokerService.stop();
                       brokerService.waitUntilStopped();
                       TimeUnit.SECONDS.sleep(10);
                   }

               } catch (Exception e) {
                   e.printStackTrace();
                   throwables.add(e);
               }

           }

       };
       restartThread.start();

       MDBMessageSendTxClientExample.main(null);
       done.set(true);
       restartThread.join(TimeUnit.SECONDS.toMillis(30));
       if (!throwables.isEmpty())  {
           throw throwables.get(0);
       }

   }


}

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

import org.apache.activemq.broker.BrokerService;
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

import java.io.InputStream;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         5/21/12
 */
@RunAsClient
@RunWith(Arquillian.class)
public class MDBCMTTxSendRunnerTest
{
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
       final BrokerService brokerService = new BrokerService();
       final Vector<Exception> throwables = new Vector<Exception>();
       brokerService.setDataDirectory("target");
       brokerService.setBrokerName("willFailOver");
       brokerService.addConnector("tcp://localhost:61616");
       brokerService.setDeleteAllMessagesOnStartup(true);
       brokerService.start();
       System.out.println("Started broker: " + brokerService + " brokerService.addr:" + brokerService.getAdminView().getTransportConnectors());

       Thread restartThread = new Thread() {

           @Override
           public void run() {

               try {
                   while (brokerService.getAdminView().getTotalDequeueCount() < 200) {
                       TimeUnit.MILLISECONDS.sleep(200);
                   }
                   System.out.println("Stopping broker on dequeue count:" + brokerService.getAdminView().getTotalDequeueCount());
                   brokerService.stop();
                   brokerService.waitUntilStopped();
                   System.out.println("Restarting broker...");
                   brokerService.setDeleteAllMessagesOnStartup(false);
                   brokerService.start(true);
                   brokerService.waitUntilStarted();
                   System.out.println("Restarted broker: " + brokerService  + " brokerService.addr:" + brokerService.getAdminView().getTransportConnectors());

               } catch (Exception e) {
                   e.printStackTrace();
                   throwables.add(e);
               }

           }

       };
       restartThread.start();

       MDBMessageSendTxClientExample.main(null);
       restartThread.join(TimeUnit.SECONDS.toMillis(30));
       if (!throwables.isEmpty())  {
           throw throwables.get(0);
       }

   }


}

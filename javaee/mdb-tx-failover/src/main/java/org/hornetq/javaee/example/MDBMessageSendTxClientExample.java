/*
 * Copyright 2009 Red Hat, Inc.
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.hornetq.javaee.example;

import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TransactionRolledBackException;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class MDBMessageSendTxClientExample
{
   static Logger LOG = LoggerFactory.getLogger(MDBMessageSendTxClientExample.class);
   public static void main(String[] args) throws Exception
   {

      Connection connection = null;
      InitialContext initialContext = null;
      try
      {
         //Step 1. Create an initial context to perform the JNDI lookup.
         final Properties env = new Properties();

         env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");

         env.put(Context.PROVIDER_URL, "remote://localhost:4447");

         env.put(Context.SECURITY_PRINCIPAL, "guest");

         env.put(Context.SECURITY_CREDENTIALS, "password");

         initialContext = new InitialContext(env);


         //Step 2. Perfom a lookup on the queue
         Queue queue = (Queue) initialContext.lookup("jms/queues/testQueue");

         //Step 3. Perform a lookup on the Connection Factory
         ConnectionFactory cf = (ConnectionFactory) initialContext.lookup("jms/RemoteConnectionFactory");

         //Step 4.Create a JMS Connection
         connection = cf.createConnection("guest", "password");

         //Step 5. Create a JMS Session
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

         //Step 6. Create a JMS Message Producer
         MessageProducer producer = session.createProducer(queue);

         connection.start();

          System.out.println("Client using connection: " + connection);
          Vector<String> replys = new Vector<String>();
          HashMap<String, Exception> exMap = new HashMap<String, Exception>();
          final int messagecount = 5000;
          CountDownLatch countDownLatch = new CountDownLatch(messagecount);
          for (int i=0; i<messagecount; i++) {
              //Step 7. Create a Text Message
              TextMessage message = session.createTextMessage(">>"+i +"<<" );

              LOG.info("Sent message: " + message.getText());

              //Step 8. Send the Message
              producer.send(message);
              replys.add("r:"+ message.getText());
          }
          session.commit();

          LOG.info("Sent all using connection: " + connection);

          queue = (Queue) initialContext.lookup("jms/queues/replyQueue");

          MessageConsumer messageConsumer = session.createConsumer(queue);

          System.out.println("Consuming all using : " + messageConsumer);

          TextMessage message;
          while (countDownLatch.getCount() > 0) {
              int tryCount = 0;
              do {
                message = (TextMessage) messageConsumer.receive(5000);
                if (message == null) {
                    LOG.info("for message " + countDownLatch.getCount() + " try:" + ++tryCount);
                    LOG.info("pending replys: " + replys);
                    if (tryCount > 3) {
                        LOG.info("exceptionMap: " + exMap.keySet());

                        // is this an indoubt case of local tx commit failure
                        if (!exMap.isEmpty()) {
                            if (exMap.get(replys.get(0)) instanceof TransactionRolledBackException) {

                                // commit in doubt
                                System.out.println("found ex for reply: " + replys.get(0) + ", " + exMap.get(replys.get(0)));
                                countDownLatch.countDown();
                                replys.remove(0);
                                break;
                            }
                        }
                    }
                }
              } while (message == null);

              if (message != null) {
                  LOG.info("reply:" + message.getJMSMessageID() + " with: " + message.getText() + " from producer tx: " + message.getStringProperty("JMSXProducerTXID"));
                  try {
                      session.commit();
                      countDownLatch.countDown();
                      replys.remove(message.getText());
                  } catch (TransactionRolledBackException indoubt) {
                      LOG.info("rollback exception on commit, rollback for receive:" + message.getText() + ", reason: " + indoubt);
                      exMap.put(message.getText(), indoubt);
                      session.rollback();
                  } catch (Exception sometimes) {
                      LOG.error("exception on commit, rollback for receive:" + message + ", reason: " + sometimes, sometimes);
                      sometimes.printStackTrace();
                      session.rollback();
                  }
              }
          }

          LOG.info("Consumed all  : " + countDownLatch.getCount() + ", replys:" + replys + ", exceptionMap:" + exMap);
      }
      finally
      {
         //Step 19. Be sure to close our JMS resources!
         if (initialContext != null)
         {
            initialContext.close();
         }
         if(connection != null)
         {
            connection.close();
         }
      }
   }
}
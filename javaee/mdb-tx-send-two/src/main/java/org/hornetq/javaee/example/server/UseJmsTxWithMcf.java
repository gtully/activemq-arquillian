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
package org.hornetq.javaee.example.server;

import javax.annotation.Resource;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.jms.TextMessage;


@Stateless
public class UseJmsTxWithMcf {
    @Resource(mappedName = "java:/JmsForU")
    ConnectionFactory connectionFactory;

    @Schedule(dayOfWeek = "0-7", hour = "*", minute = "*", second = "*/5", persistent = false)
    @TransactionAttribute(TransactionAttributeType.NEVER)
    public void doWork() throws Exception {

        System.out.println(this + ", in doWork - @Schedule");

        Connection connection = connectionFactory.createConnection();
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer messageProducer = session.createProducer(session.createQueue("testQueue"));
        TextMessage message = session.createTextMessage("This is a text message");
        messageProducer.send(message);

        System.out.println(this + ", committing now");
        session.commit();
        connection.close();
    }
}
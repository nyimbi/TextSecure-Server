/**
 * Copyright (C) 2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.push;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.entities.PendingMessage;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.PubSubManager;
import org.whispersystems.textsecuregcm.storage.PubSubMessage;
import org.whispersystems.textsecuregcm.storage.StoredMessages;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.SystemMapper;
import org.whispersystems.textsecuregcm.websocket.WebsocketAddress;

import static com.codahale.metrics.MetricRegistry.name;

public class WebsocketSender {

  private static final Logger logger = LoggerFactory.getLogger(WebsocketSender.class);

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);

  private final Meter websocketOnlineMeter  = metricRegistry.meter(name(getClass(), "ws_online"  ));
  private final Meter websocketOfflineMeter = metricRegistry.meter(name(getClass(), "ws_offline" ));

  private final Meter apnOnlineMeter        = metricRegistry.meter(name(getClass(), "apn_online" ));
  private final Meter apnOfflineMeter       = metricRegistry.meter(name(getClass(), "apn_offline"));

  private static final ObjectMapper mapper = SystemMapper.getMapper();

  private final StoredMessages storedMessages;
  private final PubSubManager  pubSubManager;

  public WebsocketSender(StoredMessages storedMessages, PubSubManager pubSubManager) {
    this.storedMessages = storedMessages;
    this.pubSubManager  = pubSubManager;
  }

  public boolean sendMessage(Account account, Device device, PendingMessage pendingMessage, boolean apn) {
    try {
      String           serialized    = mapper.writeValueAsString(pendingMessage);
      WebsocketAddress address       = new WebsocketAddress(account.getNumber(), device.getId());
      PubSubMessage    pubSubMessage = new PubSubMessage(PubSubMessage.TYPE_DELIVER, serialized);

      if (pubSubManager.publish(address, pubSubMessage)) {
        if (apn) apnOnlineMeter.mark();
        else     websocketOnlineMeter.mark();

        return true;
      } else {
        if (apn) apnOfflineMeter.mark();
        else     websocketOfflineMeter.mark();

        storedMessages.insert(address, pendingMessage);
        pubSubManager.publish(address, new PubSubMessage(PubSubMessage.TYPE_QUERY_DB, null));
        return false;
      }
    } catch (JsonProcessingException e) {
      logger.warn("WebsocketSender", "Unable to serialize json", e);
      return false;
    }
  }
}

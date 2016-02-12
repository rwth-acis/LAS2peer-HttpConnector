package i5.las2peer.httpConnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.httpConnector.client.AccessDeniedException;
import i5.las2peer.httpConnector.client.AuthenticationFailedException;
import i5.las2peer.httpConnector.client.Client;
import i5.las2peer.httpConnector.client.ConnectorClientException;
import i5.las2peer.httpConnector.client.NotFoundException;
import i5.las2peer.httpConnector.client.ServerErrorException;
import i5.las2peer.httpConnector.client.TimeoutException;
import i5.las2peer.httpConnector.client.UnableToConnectException;
import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.tools.SimpleTools;

public class ClientPastryTest {

	public static final ServiceNameVersion testServiceClass = new ServiceNameVersion(i5.las2peer.testing.TestService.class.getName(),"1.0");

	private PastryNodeImpl node;

	@Before
	public void setup() throws Exception {
		// start a launcher
		node = new PastryNodeImpl(9001, null);
		node.launch();

		UserAgent agent = MockAgentFactory.getEve();
		agent.unlockPrivateKey("evespass");
		node.storeAgent(agent);
		agent = MockAgentFactory.getAdam();
		agent.unlockPrivateKey("adamspass");
		node.storeAgent(agent);

		final HttpConnector connector = new HttpConnector();
		connector.setPort(38080);
		connector.start(node);

		String passPhrase = SimpleTools.createRandomString(20);

		ServiceAgent myAgent = ServiceAgent.createServiceAgent(testServiceClass, passPhrase);
		myAgent.unlockPrivateKey(passPhrase);

		node.registerReceiver(myAgent);
	}

	@After
	public void tearDown() {
		node.shutDown();
	}

	@Test
	public void test() throws MalformedXMLException, IOException, UnableToConnectException,
			AuthenticationFailedException, TimeoutException, ServerErrorException, AccessDeniedException,
			NotFoundException, ConnectorClientException, InterruptedException {
		UserAgent adam = MockAgentFactory.getAdam();

		System.out.println("adam: " + adam.getId());

		Client c = new Client("localhost", 38080, "" + adam.getId(), "adamspass");
		c.setSessionTimeout(1000);
		c.connect();

		c.invoke(testServiceClass.getName(), "storeEnvelopeString", "ein test");

		Object result = c.invoke(testServiceClass.getName(), "getEnvelopeString", new Object[0]);

		assertEquals("ein test", result);

		Thread.sleep(500);
		c.disconnect();

		UserAgent eve = MockAgentFactory.getEve();
		System.out.println("eve: " + eve.getId());

		Client c2 = new Client("localhost", 38080, "" + eve.getId(), "evespass");
		c.setSessionOutdate(3000);
		c.connect();

		try {
			result = c2.invoke(testServiceClass.getName(), "getEnvelopeString", new Object[0]);
			fail("AccessDeniedException expected");
		} catch (AccessDeniedException e) {
		}

	}

}

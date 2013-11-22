package de.grundid.fritz;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.DatatypeConverter;

import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import de.grundid.fritz.entity.SessionInfo;

public class FritzTemplate {

	private static final String EMPTY_SESSION_ID = "0000000000000000";
	private String baseUrl = "http://fritz.box";
	private RestTemplate restOperations;
	private String sessionId;
	private String password;

	public FritzTemplate(RestTemplate restOperations, String password) {
		this.restOperations = restOperations;
		this.password = password;
		restOperations.getMessageConverters().add(new FormHttpMessageConverter());
	}

	protected String createChallengeResponse(String challenge, String password) {
		try {
			byte[] text = (challenge + "-" + password).getBytes(Charset.forName("utf-16le"));
			byte[] digest = MessageDigest.getInstance("md5").digest(text);
			return challenge + "-" + DatatypeConverter.printHexBinary(digest).toLowerCase();
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private MultiValueMap<String, String> prepareRequest() {
		if (sessionId == null)
			getSessionId();
		MultiValueMap<String, String> request = new LinkedMultiValueMap<String, String>();
		return request;
	}

	public String getSessionId() {
		SessionInfo sessionInfo = restOperations.getForObject(baseUrl + "/login_sid.lua", SessionInfo.class);
		if (sessionInfo.getSid().equals(EMPTY_SESSION_ID)) {
			String response = createChallengeResponse(sessionInfo.getChallenge(), password);
			MultiValueMap<String, String> request = new LinkedMultiValueMap<String, String>();
			request.add("page", "");
			request.add("username", "");
			request.add("response", response);
			sessionInfo = restOperations.postForObject(baseUrl + "/login_sid.lua", request, SessionInfo.class);
			if (sessionInfo.getSid().equals(EMPTY_SESSION_ID))
				throw new CannotLoginException();
		}
		sessionId = sessionInfo.getSid();
		return sessionId;
	}

	public void activateGuestAccess(String guestSsid, String wpaKey) {
		activateGuestAccess(guestSsid, wpaKey, null);
	}

	public void activateGuestAccess(String guestSsid, String wpaKey, GuestAccessSettings settings) {
		MultiValueMap<String, String> request = prepareRequest();
		request.add("activate_guest_access", "on");
		request.add("guest_ssid", guestSsid);
		request.add("wlan_security", "0");
		request.add("wpa_key", wpaKey);
		request.add("wpa_modus", "4");
		request.add("btnSave", "");
		if (settings != null) {
			if (settings.hasTimeout()) {
				request.add("down_time_activ", "on");
				request.add("down_time_value", "" + settings.getDisableAfterTime().getMinutes());
			}
		}
		restOperations.postForEntity(baseUrl + "/wlan/guest_access.lua?sid={sid}", request, String.class, sessionId);
	}

	public void deactivateGuestAccess() {
		MultiValueMap<String, String> request = prepareRequest();
		request.add("btnSave", "");
		restOperations.postForEntity(baseUrl + "/wlan/guest_access.lua?sid={sid}", request, String.class, sessionId);
	}
}

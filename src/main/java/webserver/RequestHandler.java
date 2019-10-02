package webserver;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestHandler extends Thread {

	private static final int NOT_EXISTS = -1;

	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private Socket connection;

	public RequestHandler(Socket connectionSocket) {
		this.connection = connectionSocket;
	}

	public void run() {
		log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
			connection.getPort());

		try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {

			HttpRequest request = new HttpRequest(in);

			String path = getDefualtPath(request.getPath());

			Map<String, String> cookies = new HashMap<String, String>();
			cookies.put("logined", "false");

			if(request.getHeader("Cookie") != null) {
				cookies = HttpRequestUtils.parseCookies(request.getHeader("Cookie"));

			}

			String contentType = null;
			int contentTypeIndex = request.getHeader("Accept").indexOf(",");

			if (contentTypeIndex <= NOT_EXISTS) {
				contentType = request.getHeader("Accept");
			} else {
				contentType = request.getHeader("Accept").substring(0, contentTypeIndex);
			}

			DataOutputStream dos = new DataOutputStream(out);

			if ("/user/create".equals(path)) {
				User user = new User(
					request.getParameter("userId"),
					request.getParameter("password"),
					request.getParameter("name"),
					request.getParameter("email"));
				DataBase.addUser(user);

				response302Header(dos, "/index.html");
			} else if ("/user/login".equals(path)) {
				String url = "";

				User user = DataBase.findUserById(request.getParameter("userId"));
				boolean logined = false;

				if (user == null) {
					url = "/user/login_failed.html";
				} else {
					if (user.getPassword().equals(request.getParameter("password"))) {
						url = "/index.html";
						logined = true;
					} else {
						url = "/user/login_failed.html";
					}
				}
				response302LoginHeader(dos, logined, url);

			} else if ("/user/list".equals(path)) {
				boolean isLogined = Boolean.parseBoolean(cookies.get("logined"));

				if (isLogined) {
					String html = getUserListPage();
					makeUserList200Response(dos, contentType, html.getBytes());
				} else {
					response302LoginHeader(dos, isLogined, "login.html");
				}
			} else {
				make200Response(dos, contentType, path);
			}
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private String getDefualtPath(String path) {
		if (path.equals("/")) {
			return "/index.html";
		}
		return path;
	}

	private String getUserListPage() {
		List<User> userList = new ArrayList<>(DataBase.findAll());

		StringBuilder sb = new StringBuilder(
			"<html><head><title>사용자 목록</title></head><body><h1>사용자목록</h1>\n");

		for (User user : userList) {
			sb.append("<div>" + user.toString() + "</div>");
		}

		return sb.append("</body></html>").toString();
	}

	private void makeUserList200Response(DataOutputStream dos, String contentType, byte[] body)
		throws IOException {

		response200Header(dos, contentType, body.length);
		responseBody(dos, body);

	}

	private void make200Response(DataOutputStream dos, String contentType, String url)
		throws IOException {

		byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
		response200Header(dos, contentType, body.length);
		responseBody(dos, body);
	}

	private void response200Header(DataOutputStream dos, String contentType, int bodyLength) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: " + contentType + ";charset=utf-8\r\n");
			dos.writeBytes("Content-Length: " + bodyLength + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response302Header(DataOutputStream dos, String url) {
		try {
			dos.writeBytes("HTTP/1.1 302 Found \r\n");
			dos.writeBytes("Location: " + url + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response302LoginHeader(DataOutputStream dos, boolean status, String url) {
		try {
			dos.writeBytes("HTTP/1.1 302 Found \r\n");
			dos.writeBytes("Set-Cookie: logined=" + status + "; Path=/\r\n");
			dos.writeBytes("Location: " + url + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void responseBody(DataOutputStream dos, byte[] body) {
		try {
			dos.write(body, 0, body.length);
			dos.flush();
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
package net.krautchan.android.network;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import net.krautchan.android.Defaults;
import net.krautchan.android.Eisenheinrich;
import net.krautchan.android.Globals;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/*
 * Copyright (C) 2012 Johannes Jander (johannes@jandermail.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class CookieHelper {
	public static void getSessionCookie(final Globals globs) {
		if (globs.getSessionCookie() != null) {
			return;
		}
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				DefaultHttpClient httpClient = Eisenheinrich.getInstance().getHttpClient();

				HttpGet req = new HttpGet("http://krautchan.net/ajax/checkpost?board=kc");
				CookieStore cookieStore = new BasicCookieStore();
				httpClient.setCookieStore(cookieStore);
				HttpResponse res;
				try {
					res = httpClient.execute(req);
					List<Cookie> cookies = cookieStore.getCookies();
					for (Cookie c : cookies) {
						if (c.getName().equals("desuchan.session")) {
							globs.setSessionCookie(c);
						}
					}
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// TODO Auto-generated method stub

			}
		}, 100);
	}

	public static void getMyIP(final Globals globs) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Document doc = Jsoup
						.connect("http://krautchan.net/banned")
						.userAgent(Eisenheinrich.GLOBALS.getUserAgentString())
						.cookie("auth", "token").timeout(3000).get();
					Elements boardlist = doc.select("div.box>p>b");
					Iterator<Element> iter = boardlist.iterator();
					Element elem = null;
					while (iter.hasNext() && (null == elem)) {
						elem = iter.next();
					}
					if (null != elem) {
						String ipStr = elem.getAllElements().html();
						globs.setIpNumber(ipStr);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}
}

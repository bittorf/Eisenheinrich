package net.krautchan.backend;

/*
* Copyright (C) 2011 Johannes Jander (johannes@jandermail.de)
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

import java.util.Iterator;
import net.krautchan.data.*;

public class HtmlCreator {

	public static String htmlForPosting (KCPosting p) {
		String innerHtml = "<div class=\"posthead\">" +
		"<p class=\"headline\"><b>"+p.kcNummer+"</b><time class='timeago' datetime='"+p.creationDate+"'>"+p.creationDate+"</time></p>";
		if (null != p.title) {
			innerHtml += "<p class=\"topic\">"+p.title+"</p>";
		}
		innerHtml += "</div>";
		//innerHtml += p.content.replaceAll("\"", "&amp;quot;").replaceAll("'", "&amp;quot;");
		innerHtml += p.content;
		if (p.imgs.length > 0) {
			innerHtml += "<div class=\"image-container\">";
			for (int i = 0; i < p.imgs.length; i++) {
				if (null != p.imgs[i]) {
					innerHtml += "<a href=\"/files/"+p.imgs[i]+"\" onclick=\"Android.openImage('"+p.imgs[i]+"');return false;\"><img src=\"/thumbnails/"+p.thumbs[i]+"\"></a>";
				}
			}
			innerHtml += "</div>";
		}
		return "<div id='"+p.kcNummer+"'>"+innerHtml+"</div>";
	}
	
	public static String htmlForThread (KCThread thread, String template) {
		KCPosting p = null;
		StringBuffer buf = new StringBuffer();
		Iterator<KCPosting> iter = thread.getSortedPostings().iterator();
		boolean even = false;
		while (iter.hasNext()) {
			p = iter.next();
			String innerHtml = "<div class=\"posthead\">" +
					"<p class=\"headline\"><b>"+p.kcNummer+"</b><time class='timeago' datetime='"+p.creationDate+"'>"+p.creationDate+"</time></p>";
			if (null != p.title) {
				innerHtml += "<p class=\"topic\">"+p.title+"</p>";
			}
			innerHtml += "</div>";
			innerHtml += p.content;
			if (p.imgs.length > 0) {
				innerHtml += "<div class=\"image-container\">";
				for (int i = 0; i < p.imgs.length; i++) {
					if (null != p.imgs[i]) {
						innerHtml += "<a href=\"/files/"+p.imgs[i]+"\" onclick=\"Android.openImage('"+p.imgs[i]+"');return false;\"><img src=\"/thumbnails/"+p.imgs[i]+"\"></a>";
					}
				}
				innerHtml += "</div>";
			}
			String classStr = "";	
			if (thread.previousLastKcNum != null) {
				if (p.kcNummer <= thread.previousLastKcNum) {
					classStr = "class='collapsed'";
				} else {
					classStr = "class='unread'";
				}
			} 
			if (even) {
				if (classStr.length() == 0) {
					classStr = "class='even'";
				} else {
					classStr += " even";
				}
			}
			buf.append ("<li "+classStr+" id='"+p.dbId+"'>");
			buf.append ("<div id='"+p.kcNummer+"'>"+innerHtml+"</div></li>");
			even = !even;
		}
		String html = template.replace("</ul>",buf.toString()+"</ul>"); 
		//FileHelpers.writeToSDFile("out_"+(new Date().getTime()+".html"), html);
		return html;
	}
	
	
	/*public static String addPostings (List<KCPosting> postings, String template) {
		Collections.sort(postings, new Comparator<KCPosting>() {
			@Override
			public int compare(KCPosting p1, KCPosting p2) {
				return p1.kcNummer.intValue() - p2.kcNummer.intValue();
			}
			
		});
		String html = template;
		for (KCPosting post : postings) {
			String innerHtml = "<p class=\"headline\"><b>"+post.kcNummer+"</b><time class='timeago' datetime='"+post.creationDate+"'>"+post.creationDate+"</time></p>";
			if (null != post.title) {
				innerHtml += "<p class=\"topic\">"+post.title+"</p>";
			}
			innerHtml += post.content;
			if (post.imgs.length > 0) {
				innerHtml += "<div class=\"image-container\">";
				for (int i = 0; i < post.imgs.length; i++) {
					if (null != post.imgs[i]) {
						innerHtml += "<a href=\"/files/"+post.imgs[i]+"\" onclick=\"Android.openImage('"+post.imgs[i]+"');return false;\"><img src=\"/thumbnails/"+post.imgs[i]+"\"></a>";
					}
				}
				innerHtml += "</div>";
			}
			html = html.replace("</ul>", "<li id='"+post.dbId+"'><div id='"+post.kcNummer+"'>"+innerHtml+"</div></li></ul>");
		}
		//FileHelpers.writeToSDFile("out_"+(new Date().getTime()+".html"), html);
		return html;
	}*/
}

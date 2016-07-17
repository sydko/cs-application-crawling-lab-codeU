package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Node;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
        // FILL THIS IN!
        Elements paragraphs; //the paragraphs of a page

		String url = queue.poll();// Get's the first list off the queue
        if (testing){    	
        	paragraphs = wf.readWikipedia(url); // Read page using WikiFetcher.readWikipedia
        	index.indexPage(url, paragraphs); //indexes pages regardless if theyve been read or not
        	return url;

        } else {
        	if(index.isIndexed(url)){
        		// if the url is indexed it should not index it again
        		return null;
        	} else { //else it should read the contents of the page
        		
        		paragraphs = wf.fetchWikipedia(url); // read page using WikiFetcher.readWikipedia
        		
        		index.indexPage(url, paragraphs); //should index the page,
        		queueInternalLinks(paragraphs);
        		return url;

        	}
        }
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
		//need to use wikinodeIterable

	   for (Element paragraph : paragraphs){
			queueParagraphLinks(paragraph);
		}
	}
	// queue.add("new link")
	// 

	private void queueParagraphLinks(Node root) {
		// create an Iterable that traverses the tree
		Iterable<Node> nt = new WikiNodeIterable(root);

		// loop through the nodes
		for (Node node: nt) {
			// process elements to get find links
			if (node instanceof Element) {
				Element link = (Element) node;
				if ((link != null) && validLink(link)){
					queue.add((String) link.text());
				} 
			}
		}
	}

	/**
	 * Checks whether a link is value.
	 * 
	 * @param elt
	 * @return
	 */
	private boolean validLink(Element elt) {
		// it's no good if it's
		// not a link
		if (!elt.tagName().equals("a")) {
			return false;
		}
		// in italics
		if (isItalic(elt)) {
			return false;
		}
		// // in parenthesis
		// if (isInParens(elt)) {
		// 	return false;
		// }
		// a bookmark
		if (startsWith(elt, "#")) {
			return false;
		}
		// a Wikipedia help page
		if (startsWith(elt, "/wiki/Help:")) {
			return false;
		}
		// TODO: there are a couple of other "rules" we haven't handled
		return true;
	}

	/**
	 * Checks whether a link starts with a given String.
	 * 
	 * @param elt
	 * @param s
	 * @return
	 */
	private boolean startsWith(Element elt, String s) {
		//System.out.println(elt.attr("href"));
		return (elt.attr("href").startsWith(s));
	}

	// /**
	//  * Checks whether the element is in parentheses (possibly nested).
	//  * 
	//  * @param elt
	//  * @return
	//  */
	// private boolean isInParens(Element elt) {
	// 	// check whether there are any parentheses on the stack
	// 	return !parenthesisStack.isEmpty();
	// }

	/**
	 * Checks whether the element is in italics.
	 * 
	 * (Either a "i" or "em" tag)
	 * 
	 * @param start
	 * @return
	 */
	private boolean isItalic(Element start) {
		// follow the parent chain until we get to null
		for (Element elt=start; elt != null; elt = elt.parent()) {
			if (elt.tagName().equals("i") || elt.tagName().equals("em")) {
				return true;
			}
		}
		return false;
	}


	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}

package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.util.TreeOperator;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;

/**
 * The idea is that when a quarter expression like first, second, third, fourth
 * quarter, appears directly after a reporting verb, it tends to be the case that
 * the expression is not a temporal argument but rather modifies one of the verbs 
 * arguments. For example "company reported third quarter losses of $3 million...",
 * "third quarter" modifies" losses, and "third quarter losses" is itself an argument
 * of "reported".
 * 
 * @author cassidy
 */
public class QuarterSieveReporting implements Sieve {
	private String valQuarterRegex = "\\d{4}-Q\\d";
	private Pattern valQuarter = Pattern.compile(valQuarterRegex);
	private String textQuarterRegex = 
			"(first|second|third|fourth|1st|2nd|3rd|4th)(\\s|-)quarter";
	private Pattern textQuarter = Pattern.compile(textQuarterRegex);
	
	private static TreeFactory tf = new LabeledScoredTreeFactory();
	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		// The size of the list is the number of sentences in the document.
		// The inner list is the events in textual order.
		List<List<TextEvent>> allEvents = info.getEventsBySentence(docname);
		List<List<Timex>> allTimexes = info.getTimexesBySentence(docname);
		List<String> allParseStrings = info.getParses(docname);
		
		// Fill this with our new proposed TLinks.
		List<TLink> proposed = new ArrayList<TLink>();
		
		// Make BEFORE links between all intra-sentence pairs.
		int sid = 0;
		for( Sentence sent : info.getSentences(docname) ) {
			System.out.println("DEBUG: adding tlinks from " + docname + " sentence " + sent.sentence());
			Tree sentParseTree = sidToTree(sid, allParseStrings);
			for (Timex timex : allTimexes.get(sid)) {
				if (!validateTimex(timex)) continue;
				for (TextEvent event : allEvents.get(sid)) {
					if (!validateEvent(event)) continue;
					int timexIndex = timex.offset();
					int eventIndex = event.index();
					if (timexIndex - 1 == eventIndex) {
						TLink.TYPE label = TLink.TYPE.IS_INCLUDED;
						proposed.add(new EventTimeLink(event.eiid() , timex.tid(), label));
					}
					else if (timexIndex - 2 == event.index()) {
						int interIndex = timexIndex - 1;
						String interWord = getTokenText(interIndex, sent);
						TLink.TYPE label = classifyInter(interWord);
						proposed.add(new EventTimeLink(event.eiid() , timex.tid(), label));
					}
				}
			}
			sid++;
		}
		
		System.out.println("TLINKS: " + proposed);
		return proposed;
	}
	
	private String getTokenText(int index, Sentence sent) {
		List<CoreLabel> tokens = sent.tokens();
		CoreLabel token = tokens.get(index);
		return token.originalText();
	}

	// returns the label appropriate for the word between the verb and quarter timex
	// could be improved by checking to see if the quarter is to occur in the future,
	// in which case BEFORE might be more appropriate
	private TLink.TYPE classifyInter(String interText){
		if (interText == "in") return TLink.TYPE.IS_INCLUDED;
		else {
			return TLink.TYPE.AFTER;
		}
	}
	
	private Boolean validateEvent(TextEvent event){
		if (event.getTheClass()=="REPORTING") return true;
		else return false;
	}
	
	/**
	 * validateTime ensures that timex value is of a certain form
	 * one option is: YYYY-MM-DD or YYYY-MM or YYYY
	 * The idea is that some time expressions are modifiers of arguments
	 * of the verb, and not arguments themselves. 
	 * For example, "X said Tuesday that ..." vs. "X said Tuesday's earnings were ..."
	 * In the latter case, 'said' may not be included_in Tuesday. A very common example
	 * is when the time expression is a quarter, since the verb is almost always after
	 * the quarter. 
	 */
	
	private Boolean validateTimex(Timex timex){
		String val = timex.value();
		// check if value represent a quarter
		Matcher matchVal = valQuarter.matcher(val);
		Matcher matchText = valQuarter.matcher(val);
		if (matchText.matches() && matchVal.matches()) return true;
		else return false;
	}
	
	private String posTagFromTree(Tree sentParseTree, int tokenIndex){
		String posTag = TreeOperator.indexToPOSTag(sentParseTree, tokenIndex);
		return posTag;
	}
	
	private Tree sidToTree(int sid, List<String> allParseStrings) {
		String sentParseString = allParseStrings.get(sid);
		Tree sentParseTree = TreeOperator.stringToTree(sentParseString, tf);
		return sentParseTree;
	}
	/**
	 * No training. Just rule-based.
	 */
	public void train(InfoFile trainingInfo) {
		// no training
	}

}

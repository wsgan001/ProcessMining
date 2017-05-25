package org.processmining.plugins.cnmining;
 
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIntOpenHashMap;
import com.carrotsearch.hppc.IntOpenHashSet;
import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.ObjectContainer;
import com.carrotsearch.hppc.ObjectIntOpenHashMap;
import com.carrotsearch.hppc.ObjectLookupContainer;
import com.carrotsearch.hppc.ObjectObjectOpenHashMap;
import com.carrotsearch.hppc.ObjectOpenHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.fluxicon.slickerbox.components.NiceSlider;
import com.fluxicon.slickerbox.factory.SlickerFactory;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.deckfour.uitopia.api.event.TaskListener;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.contexts.cli.CLIPluginContext;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.util.ui.widgets.ProMPropertiesPanel;
import org.processmining.models.causalnet.CausalNetAnnotations;
import org.processmining.models.causalnet.CausalNetAnnotationsConnection;
import org.processmining.models.connections.GraphLayoutConnection;
import org.processmining.models.connections.flexiblemodel.FlexEndTaskNodeConnection;
import org.processmining.models.connections.flexiblemodel.FlexStartTaskNodeConnection;
import org.processmining.models.flexiblemodel.EndTaskNodesSet;
import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexFactory;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;
import org.processmining.models.flexiblemodel.StartTaskNodesSet;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.jgraph.ProMJGraph;
import org.processmining.models.jgraph.ProMJGraphVisualizer;
import org.processmining.models.jgraph.visualization.ProMJGraphPanel;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.pnml.Pnml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
 
public class CNMining
{
	public static String attivita_iniziale = "_START_";
	public static String attivita_finale = "_END_";
   
	public static long time;
	   
	@Plugin(
		name="CNMining", 
		parameterLabels = { "log" }, 
		returnLabels = { 
			"CausalNet", 
			"StartTaskNodesSet", 
			"EndTaskNodesSet", 
			"CausalNetAnnotations"
		}, 
		returnTypes={
			Flex.class, 
			StartTaskNodesSet.class, 
			EndTaskNodesSet.class, 
			CausalNetAnnotations.class
		}, 
		userAccessible=true, help="??"
	)
	@UITopiaVariant(
		affiliation="DIMES University of Calabria", 
		author="F. Lupia", 
		email="lupia@dimes.unical.it"
	)
	public static Object[] run(UIPluginContext context, XLog log) throws Exception
	{
		SettingsView settingsView = new SettingsView(context, log);
		Settings settings = settingsView.show();
     
	    return startCNMining(context, log, settings);
	}
	
	public static Object[] startCNMining(UIPluginContext context, XLog log, Settings settings) throws Exception
	{
		ConstraintsManager vincoli = new ConstraintsManager();
		
		context.getProgress().setValue(1);
		
		System.out.println("\n\nCNMining\n\nSettings:\n");
	    System.out.println("- sigma log noise " + settings.sigmaLogNoise);
	    System.out.println("- delta fall factor  " + settings.fallFactor);
	    System.out.println("- relative to best  " + settings.relativeToBest);
		
		boolean enable_constraints = false;
     
		if (settings.areConstraintsAvailable()) {
			if (settings.constraintsFilename.equals("")) {
				JOptionPane.showMessageDialog(null, "Incorrect path to constraints file\nThe algoritm will now run without constraints...");
				enable_constraints = false;
			}
			else {
				ConstraintParser cp = new ConstraintParser(settings.constraintsFilename);
				boolean validFile = cp.run();
				
				if (!validFile) {
					JOptionPane.showMessageDialog(null, "Invalid constraints file\nThe algoritm will now run without constraints...");
					enable_constraints = false;
				}
				else {
					ObjectArrayList<Constraint> constraints = cp.getConstraints();
					if (constraints.size() == 0) {
						JOptionPane.showMessageDialog(null, "No constraints contained in the input file...");
					}
					for (int i = 0; i < constraints.size(); i++) {
						Constraint constr = (Constraint)constraints.get(i);
						if (constr.isPositiveConstraint()) {
							vincoli.positivi.add(constr);
						} 
						else 
						{ 
							Iterator localIterator2 = constr.getHeadList().iterator();
							Iterator localIterator1 = constr.getBodyList().iterator();
							while(localIterator1.hasNext() && localIterator2.hasNext()){
								String body = (String)localIterator1.next();
								String head = (String)localIterator2.next();
								vincoli.forbidden.add(new Forbidden(body, head));
							}
							vincoli.negati.add(constr);
						}
					}
				}
			}
		}

		CNMining cnm = new CNMining();
 
		cnm.aggiungiAttivitaFittizia(log);
		
		Object[] array = LogUnfolder.unfold(log);
     
		ObjectIntOpenHashMap<String> map = (ObjectIntOpenHashMap)array[0];
     
		Object attivita_tracce = (ObjectObjectOpenHashMap)array[1];
     
		ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita = (ObjectObjectOpenHashMap)array[2];
     
		if (enable_constraints) {
			cnm.creaVincoliUnfolded(
				vincoli.positivi, vincoli.negati, vincoli.forbidden, vincoli.positiviUnfolded, 
				vincoli.negatiUnfolded, vincoli.forbiddenUnfolded, map
			);
		}
		context.getProgress().setValue(10);
     
		System.out.println("OK1");
     
		double[][] csm = cnm.calcoloMatriceDeiCausalScore(log, map, traccia_attivita, settings.fallFactor);

		System.out.println("OK2");

		double[][] m = cnm.buildBestNextMatrix(log, map, traccia_attivita, csm, vincoli.forbiddenUnfolded);
     
		System.out.println("OK3");
		if (settings.sigmaLogNoise > 0.0D)
		{
			for (int i = 0; i < m.length; i++)
			{
				for (int j = 0; j < m.length; j++)
				{
					if (m[i][j] <= settings.sigmaLogNoise * traccia_attivita.size())
					{
						m[i][j] = 0.0D; 
					}
				}
			}
		}
		
		System.out.println();
		System.out.println("COSTRUISCO GRAFO UNFOLDED ORIGINALE SOLO LOG... ");
     
		Graph graph = new Graph();
 
		Object[] keys = map.keys;
		int[] values = map.values;
		boolean[] states = map.allocated;
 
		for (int iii = 0; iii < states.length; iii++)
		{
			if (states[iii] != false)
			{
				Node node = new Node((String)keys[iii], values[iii]);
				Object[] nKeys = graph.getMap().keys;
				boolean[] nStates = graph.getMap().allocated;
     
				boolean found = false;
				for (int jj = 0; jj < nStates.length; jj++) {
					if ((nStates[jj] != false) && 
							(nKeys[jj].equals(node))) {
						found = true;
						break;
					}
				}
				if (!found) {
					graph.getMap().put(node, new ObjectOpenHashSet<Node>());
				}
			}
		}
 
		for (int p = 0; p < m.length; p++) {
			for (int r = 0; r < m[0].length; r++)
				if (m[p][r] > 0.0D) {
					Node np = graph.getNode(cnm.getKeyByValue(map, p), p);
       
         	 Node nr = graph.getNode(cnm.getKeyByValue(map, r), r);
       
         	 graph.addEdge(np, nr, false);
       
         	 np.incr_Outer_degree();
         	 nr.incr_Inner_degree();
				}
		}
		System.out.println();
		System.out.println();
     
	    System.out.println();
 
	    System.out.println("GRAFO FOLDED ORIGINALE SOLO LOG");
     
	    ObjectIntOpenHashMap<String> folded_map = new ObjectIntOpenHashMap<String>();
	 	ObjectObjectOpenHashMap<String, ObjectArrayList<String>> folded_attivita_tracce = new ObjectObjectOpenHashMap<String, ObjectArrayList<String>>();
	  	ObjectObjectOpenHashMap<String, ObjectArrayList<String>> folded_traccia_attivita = new ObjectObjectOpenHashMap<String, ObjectArrayList<String>>();
     
	  	Graph folded_G_Ori = cnm.getGrafoAggregato(
	  		graph, log, true, folded_map, 
	  		folded_attivita_tracce, 
	  		folded_traccia_attivita
	  	);
     
	  	System.out.println();
	  	
	  	boolean vincoli_consistenti = cnm.verifica_consistenza_vincoli(vincoli.positivi, vincoli.negati);
     
	  	if (!vincoli_consistenti) {
	  		System.out.println("FALLIMENTO VINCOLI INCONSISTENTI ");
	  		System.exit(0);
	  	}
	  	
	  	if (enable_constraints) {
	  		System.out.println("STAMPA PG0 FOLDED");
       
	  		cnm.buildPG0(
  				graph, m, vincoli.positiviUnfolded, 
  				vincoli.positivi, vincoli.negatiUnfolded, 
  				vincoli.negati, vincoli.forbidden, 
  				vincoli.forbiddenUnfolded, 
  				map, (ObjectObjectOpenHashMap)attivita_tracce, 
  				traccia_attivita, csm, settings.sigmaLowCsConstrEdges, 
  				folded_G_Ori, folded_map
  			);
       
	       Graph folded_PG0 = cnm.getGrafoAggregato(
    		   graph, log, false, folded_map, folded_attivita_tracce, 
    		   folded_traccia_attivita
		   );
       
	       System.out.println();
	       
	       if (!cnm.verificaVincoliPositivi(folded_PG0, null, null, vincoli.positivi, folded_map)) {
	    	   System.out.println("FALLIMENTO PG0 NON SODDISFA I VINCOLI POSITIVI!");
	    	   System.exit(0);
	       }
	  	}
     
	  	context.getProgress().setValue(30);
	  	
	  	ObjectArrayList<FakeDependency> attivita_parallele = cnm.getAttivitaParallele(
	  		m, graph, map, vincoli.positivi, 
	  		folded_map, folded_G_Ori
		);
  	
	  	System.out.println();
	  	System.out.println("START ALGORITMO 2... ");
	  	System.out.println();
  
	  	cnm.algoritmo2(
  			m, graph, map, (ObjectObjectOpenHashMap)attivita_tracce,
  			traccia_attivita, csm, settings.sigmaUpCsDiff, folded_map, 
  			vincoli.forbidden, vincoli.positivi, vincoli.negati
		);
	  	System.out.println();
     
	  	System.out.println("GRAFO DOPO AVER APPLICATO ALGORITMO 2");
	  	System.out.println();
 
	  	System.out.println("ATTIVITA PARALLELE RESIDUE DOPO ALGORITMO 2...");
     
	  	System.out.println();
     
	  	Graph folded_g = cnm.getGrafoAggregato(
			graph, log, false, folded_map, 
	  		folded_attivita_tracce, 
	  		folded_traccia_attivita
	  	);
     
 
	  	for (int ni = 0; ni < graph.listaNodi().size(); ni++) {
	  		Node n = (Node)graph.listaNodi().get(ni);
	  		n.setMark(false);
	  	}
     
	  	ObjectArrayList<FakeDependency> attivita_parallele_residue = cnm.getAttivitaParallele(
	  		m, graph, map, 
	  		vincoli.positivi, folded_map, folded_g
	  	);
	  	
  		System.out.println();
  		System.out.println();
     
  		for (int jj = 0; jj < folded_g.getLista_archi().size(); jj++)
  		{
  			Edge e = (Edge)folded_g.getLista_archi().get(jj);
       
  			for (int kk = 0; kk < vincoli.positivi.size(); kk++) {
  				Constraint c = (Constraint)vincoli.positivi.get(kk);
  				if ((c.getBodyList().contains(e.getX().getNomeAttivita())) && (c.getHeadList().contains(e.getY().getNomeAttivita())))
  				{ 
  					e.setFlag(true);
  					System.out.println(e + " OK!!!!!!");
  					break;
  				}
  				System.out.println("NOT OK!!!!!!!");
  			}
  		}
     
  		System.out.println("GRAFO FOLDED ");
     
  		System.out.println();
  		
  		double[][] csmOri = cnm.calcoloMatriceDeiCausalScore(log, folded_map, folded_traccia_attivita, settings.fallFactor);
     
  		System.out.println();
	    context.getProgress().setValue(55);
     
	    System.out.println("POST-PROCESSING RIMOZIONE DIPENDENZE INDIRETTE... ");
	    System.out.println();
     
	    cnm.postProcessing_dip_indirette(
    		folded_g, folded_map, folded_attivita_tracce, 
    		folded_traccia_attivita, csmOri, 
    		settings.sigmaLogNoise, vincoli.positivi
    	);
	    
	    Node start = new Node(attivita_iniziale, folded_map.get(attivita_iniziale));
	    Node end = new Node(attivita_finale, folded_map.get(attivita_finale));
 
	    ObjectArrayList<Node> startActivities = new ObjectArrayList<Node>();
 
	    ObjectArrayList<Node> endActivities = new ObjectArrayList<Node>();
 
	    folded_g = cnm.rimuoviAttivitaFittizie(
	    	folded_g, folded_map, folded_traccia_attivita, 
	    	folded_attivita_tracce, start, end, 
	    	log, startActivities, endActivities
	    );
 
	    cnm.computeBindings(folded_g, folded_traccia_attivita, folded_map);
 
	    System.out.println("PROCEDURA REMOVABLE-EDGES ");
     
	    csmOri = cnm.calcoloMatriceDeiCausalScore(log, folded_map, folded_traccia_attivita, settings.fallFactor);
     
 
	    for (;;)
	    {
	    	ObjectArrayList<Edge> removableEdges = cnm.removableEdges(
	    		folded_g, csmOri, vincoli.positivi, folded_map, settings.relativeToBest
	    	);
       
	       if (removableEdges.size() == 0) {
	    	   break;
	       }
	       Edge bestRemovable = null;
       
	       double worst_causal_score = Double.MAX_VALUE;
	       
	       for (int jj = 0; jj < removableEdges.size(); jj++)
	       {
	    	   Edge e = (Edge)removableEdges.get(jj);
         
	    	   double e_cs = csmOri[e.getX().getID_attivita()][e.getY().getID_attivita()];
         
	    	   if (e_cs < worst_causal_score) {
	    		   worst_causal_score = e_cs;
	    		   bestRemovable = e;
	    	   }
	       }
       
	       folded_g.removeEdge(bestRemovable.getX(), bestRemovable.getY());
       
	       if (!cnm.verificaVincoliPositivi(folded_g, null, null, vincoli.positivi, folded_map)) {
	    	   folded_g.addEdge(bestRemovable.getX(), bestRemovable.getY(), true);
	       }
	       else
	       {
	    	   System.out.println("RIMOSSO ARCO " + bestRemovable.getX().getNomeAttivita() + " -> " + 
    			   bestRemovable.getY().getNomeAttivita());
         
 
	    	   ObjectIntOpenHashMap<IntOpenHashSet> obX = bestRemovable.getX().getOutput();         
	    	   ObjectIntOpenHashMap<IntOpenHashSet> ibY = bestRemovable.getY().getInput();
         
	    	   keys = obX.keys;
         
	    	   for (int ts = 0; ts < obX.allocated.length; ts++) {
	    		   if (obX.allocated[ts] != false) {
	    			   IntOpenHashSet tks = (IntOpenHashSet)keys[ts];
	    			   tks.remove(bestRemovable.getY().getID_attivita());
	    		   }
	    	   }
	    	   keys = ibY.keys;
         
	    	   for (int ts = 0; ts < ibY.allocated.length; ts++) {
	    		   if (ibY.allocated[ts] != false) {
	    			   IntOpenHashSet tks = (IntOpenHashSet)keys[ts];
	    			   tks.remove(bestRemovable.getX().getID_attivita());
	    		   }
	    	   }	    	   
 
	    	   ObjectIntOpenHashMap<IntArrayList> extendedObX = bestRemovable.getX().getExtendedOutput();         
	    	   ObjectIntOpenHashMap<IntArrayList> extendedIbY = bestRemovable.getY().getExtendedInput();
         
	    	   keys = extendedObX.keys;
         
	    	   for (int ts = 0; ts < extendedObX.allocated.length; ts++) {
	    		   if (extendedObX.allocated[ts] != false) {
	    			   IntArrayList tks = (IntArrayList)keys[ts];
	    			   tks.removeAllOccurrences(bestRemovable.getY().getID_attivita());
	    		   }
	    	   }
	    	   keys = extendedIbY.keys;
         
	    	   for (int ts = 0; ts < extendedIbY.allocated.length; ts++)
	    		   if (extendedIbY.allocated[ts] != false) {
	    			   IntArrayList tks = (IntArrayList)keys[ts];
	    			   tks.removeAllOccurrences(bestRemovable.getX().getID_attivita());
	    		   }
	    	   removableEdges.removeFirstOccurrence(bestRemovable);
       		}
	    }
     
	    ObjectArrayList<Node> removableNodes = new ObjectArrayList<Node>();
    	for (int jj = 0; jj < folded_g.listaNodi().size(); jj++) {
    		Node n = (Node)folded_g.listaNodi().get(jj);
    		if ((n.getInner_degree() == 0) && (n.getOuter_degree() == 0)) {
    			removableNodes.add(n);
    		}
    	}
    	for (int jj = 0; jj < removableNodes.size(); jj++) {
    		Node removableNode = (Node)removableNodes.get(jj);
    		folded_g.removeNode(removableNode);
    	}
 
    	CausalNetAnnotations annotations = new CausalNetAnnotations();
 
    	Flex flexDiagram = FlexFactory.newFlex("Causal Net CNMining");
 
    	FlexNode[] nodes = new FlexNode[folded_g.listaNodi().size()];
 
    	System.out.println("nodes length " + nodes.length);
    	System.out.println("graph length " + folded_g.listaNodi().size());
     
 
    	IntIntOpenHashMap flexMap = new IntIntOpenHashMap();
     
    	int index = 0;
     
    	String bindingsContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<ExtendedCausalNet name=\"" + 
			((XAttribute)log.getAttributes().get("concept:name")).toString() + "\"\n" + 
			"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
			"xsi:noNamespaceSchemaLocation=\"ExtendedCausalNetSchema.xsd\">\n";
     
    	for (int ii = 0; ii < folded_g.listaNodi().size(); ii++)
    	{
    		Node n = (Node)folded_g.listaNodi().get(ii);
   
    		flexMap.put(n.getID_attivita(), index);
    		
    		nodes[index] = flexDiagram.addNode(n.getNomeAttivita());
   
    		annotations.addNodeInfo(nodes[index], "id", n.getNomeAttivita());
    		index++;
   
    		bindingsContent = bindingsContent + "<Node name=\"" + n.getNomeAttivita() + "\" id=\"" + n.getID_attivita() + "\">\n" + 
				"<ExtendedInputBindings>\n";
   
    		ObjectIntOpenHashMap<IntArrayList> extendedObX = n.getExtendedOutput();
   
    		ObjectIntOpenHashMap<IntArrayList> extendedIbY = n.getExtendedInput();
   
    		keys = extendedIbY.keys;
   
    		for (int ts = 0; ts < extendedIbY.allocated.length; ts++)
    			if (extendedIbY.allocated[ts] != false) {
    				IntArrayList tks = (IntArrayList)keys[ts];
    				if (tks.size() > 0) {
    					bindingsContent = bindingsContent + "{";
    					for (int i = 0; i < tks.size() - 1; i++) {
    						bindingsContent = bindingsContent + tks.get(i) + ", ";
    					}
    					bindingsContent = bindingsContent + tks.get(tks.size() - 1) + "}\n";
    				}
    			}
    		bindingsContent = bindingsContent + "</ExtendedInputBindings>\n";
    		bindingsContent = bindingsContent + "<ExtendedOutputBindings>\n";
   
    		keys = extendedObX.keys;
   
    		for (int ts = 0; ts < extendedObX.allocated.length; ts++)
    			if (extendedObX.allocated[ts] != false) {
    				IntArrayList tks = (IntArrayList)keys[ts];
    				if (tks.size() > 0) {
    					bindingsContent = bindingsContent + "{";
    					for (int i = 0; i < tks.size() - 1; i++)
    						bindingsContent = bindingsContent + tks.get(i) + ", ";
    					bindingsContent = bindingsContent + tks.get(tks.size() - 1) + "}\n";
    				}
    			}
    		bindingsContent = bindingsContent + "</ExtendedOutputBindings>\n</Node>\n";
    	}
	    	
    	for (int ii = 0; ii < folded_g.getLista_archi().size(); ii++) {
    		Edge e = (Edge)folded_g.getLista_archi().get(ii);
   
    		flexDiagram.addArc(nodes[flexMap.get(e.getX().getID_attivita())], 
    				nodes[flexMap.get(e.getY().getID_attivita())]);
    		bindingsContent = bindingsContent + "<Edge src= \"" + e.getX().getID_attivita() + "\" dest= \"" + e.getY().getID_attivita() + "\" /> \n";
    	}
     
    	bindingsContent = bindingsContent + "</ExtendedCausalNet>\n";
 
    	File ec = new File("ExtendedCausalNet.xml");
    	if (ec.exists())
    		ec.delete();
    	ec.createNewFile();
    	try {
    		Files.write(FileSystems.getDefault().getPath(".", new String[] { "ExtendedCausalNet.xml" }), bindingsContent.getBytes(), new OpenOption[] {
			StandardOpenOption.APPEND });
    	} catch (IOException ioe) {
    		ioe.printStackTrace();
    	}
      
    	for (int ii = 0; ii < folded_g.listaNodi().size(); ii++)
    	{
    		Node n = (Node)folded_g.listaNodi().get(ii);
    		
    		keys = n.getOutput().keys;
    		
    		for (int ts = 0; ts < n.getOutput().allocated.length; ts++) {
    			if (n.getOutput().allocated[ts] != false) {
    				IntOpenHashSet se = (IntOpenHashSet)keys[ts];
       
    				SetFlex set = new SetFlex();
    				for (IntCursor o : se) {
    					set.add(nodes[flexMap.get(o.value)]);
    				}
    				if ((set.size() != 0) || (endActivities.contains(n)))
    					nodes[flexMap.get(n.getID_attivita())].addOutputNodes(set);
    			}
    		}
    		keys = n.getInput().keys;
   
    		for (int ts = 0; ts < n.getInput().allocated.length; ts++) {
    			if (n.getInput().allocated[ts] != false) {
    				IntOpenHashSet se = (IntOpenHashSet)keys[ts];
       
    				SetFlex set = new SetFlex();
    				for (IntCursor i : se) {
    					set.add(nodes[flexMap.get(i.value)]);
    				}
    				if ((set.size() != 0) || (startActivities.contains(n))) {
    					nodes[flexMap.get(n.getID_attivita())].addInputNodes(set);
    				}
    			}
    		}
    	}
     
    	StartTaskNodesSet startTaskNodes = new StartTaskNodesSet();
     
    	for (int i = 0; i < startActivities.size(); i++) {
    		Node n = (Node)startActivities.get(i);
    		SetFlex setStart = new SetFlex();
   
    		setStart.add(nodes[flexMap.get(n.getID_attivita())]);
   
    		startTaskNodes.add(setStart);
    	}     
 
    	EndTaskNodesSet endTaskNodes = new EndTaskNodesSet();
	    	
    	for (int i = 0; i < endActivities.size(); i++)
    	{
    		Node n = (Node)startActivities.get(i);
   
    		SetFlex setEnd = new SetFlex();
   
    		setEnd.add(nodes[flexMap.get(n.getID_attivita())]);
   
    		endTaskNodes.add(setEnd);
    	}     
    	for (int i = 0; i < nodes.length; i++) {
    		nodes[i].commitUpdates();
    	}
 
    	System.out.println();
      
    	context.getProgress().setValue(85);
 
    	context.getProgress().setValue(100);
 
    	context.getFutureResult(0).setLabel(flexDiagram.getLabel());
    	context.getFutureResult(1).setLabel("Start tasks node of " + flexDiagram.getLabel());
    	context.getFutureResult(2).setLabel("End tasks node of " + flexDiagram.getLabel());
    	context.getFutureResult(3).setLabel("Annotations of " + flexDiagram.getLabel());
 
    	context.addConnection(new FlexStartTaskNodeConnection("Start tasks node of " + flexDiagram.getLabel() + 
			" connection", flexDiagram, startTaskNodes));
    	context.addConnection(new FlexEndTaskNodeConnection("End tasks node of " + flexDiagram.getLabel() + 
			" connection", flexDiagram, endTaskNodes));
    	context.addConnection(new CausalNetAnnotationsConnection("Annotations of " + flexDiagram.getLabel() + 
			" connection", flexDiagram, annotations));
     
    	visualize(flexDiagram);

    	return new Object[] { flexDiagram, startTaskNodes, endTaskNodes, annotations };
	}
   
 
	public Graph rimuoviAttivitaFittizie(Graph folded_g, ObjectIntOpenHashMap<String> folded_map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_traccia, Node start, Node end, XLog log, ObjectArrayList<Node> startActivities, ObjectArrayList<Node> endActivities)
	{
		ObjectArrayList<Node> startActs = new ObjectArrayList<Node>();
		ObjectArrayList<Node> endActs = new ObjectArrayList<Node>();
     
		for (int i = 0; i < log.size(); i++)
		{
			XTrace trace = (XTrace)log.get(i);
			trace.remove(0);
			trace.remove(trace.size() - 1);
		}
		
		int startID = start.getID_attivita();     
		int endID = end.getID_attivita();
     
		attivita_traccia.remove(start.getNomeAttivita());
		attivita_traccia.remove(end.getNomeAttivita());
		
		Object[] values = traccia_attivita.values;
		boolean[] states = traccia_attivita.allocated;
     
		for (int iii = 0; iii < states.length; iii++)
		{
			if (states[iii] != false) {
				ObjectArrayList<String> vals = (ObjectArrayList)values[iii];
				vals.removeFirstOccurrence(start.getNomeAttivita());
				vals.removeFirstOccurrence(end.getNomeAttivita());
			}
		}
     
		for (int ii = 0; ii < folded_g.getLista_archi().size(); ii++) {
			Edge e = (Edge)folded_g.getLista_archi().get(ii);
			if (e.getX().equals(start)) {
				folded_g.getLista_archi().removeAllOccurrences(e);
				startActs.add(e.getY());
				folded_g.removeEdge(start, e.getY());
				e.getY().decr_Inner_degree();
				ii--;
			}
			if (e.getY().equals(end)) {
				folded_g.getLista_archi().removeAllOccurrences(e);
				endActs.add(e.getX());
				folded_g.removeEdge(e.getX(), end);
				
				e.getX().decr_Outer_degree();
				ii--;
			}
		}
 
		folded_g.getMap().remove(start);
		folded_g.getMap().remove(end);
		folded_g.listaNodi().removeFirstOccurrence(start);
		folded_g.listaNodi().removeFirstOccurrence(end);
		folded_map.remove(start.getNomeAttivita());
		folded_map.remove(end.getNomeAttivita());
     
		Graph cleanG = new Graph();
		Node n;
		for (int ii = 0; ii < folded_g.listaNodi().size(); ii++) {
			n = (Node)folded_g.listaNodi().get(ii);
			if ((n.getID_attivita() > startID) && (n.getID_attivita() < endID))
			{
				Node newNode = new Node(n.getNomeAttivita(), n.getID_attivita() - 1);
         
				newNode.setInner_degree(n.getInner_degree());
	         	newNode.setOuter_degree(n.getOuter_degree());
	         	folded_map.remove(n.getNomeAttivita());
	         	folded_map.put(newNode.getNomeAttivita(), newNode.getID_attivita());
	         	cleanG.getMap().put(newNode, new ObjectOpenHashSet());
			} 
			else if (n.getID_attivita() > endID)
			{
				Node newNode = new Node(n.getNomeAttivita(), n.getID_attivita() - 2);
				newNode.setInner_degree(n.getInner_degree());
				newNode.setOuter_degree(n.getOuter_degree());
				folded_map.remove(n.getNomeAttivita());
				folded_map.put(newNode.getNomeAttivita(), newNode.getID_attivita());
				cleanG.getMap().put(newNode, new ObjectOpenHashSet());
			}
		}
     
 
		for (ObjectCursor<Edge> ee : folded_g.getLista_archi()) {
			Edge e = (Edge)ee.value;
			cleanG.addEdge(cleanG.getNode(e.getX().getNomeAttivita(), folded_map.get(e.getX().getNomeAttivita())), 
			cleanG.getNode(e.getY().getNomeAttivita(), folded_map.get(e.getY().getNomeAttivita())), e.isFlag());
		}
		
		for (ObjectCursor<Node> n1 : startActs) {
			if (((Node)n1.value).getOuter_degree() > 0) {
				Node cn = cleanG.getNode(((Node)n1.value).getNomeAttivita(), folded_map.get(((Node)n1.value).getNomeAttivita()));
				startActivities.add(cn);
			}
		}
 
		for (ObjectCursor<Node> e : endActs) {
			if (((Node)e.value).getInner_degree() > 0) {
				Node en = cleanG.getNode(((Node)e.value).getNomeAttivita(), folded_map.get(((Node)e.value).getNomeAttivita()));
				endActivities.add(en);
			}
		}
     
		startActs = null;
		endActs = null;
		cleanG.listaNodi();
		return cleanG;
	}	
	
	public static void visualize(Flex flex)
	{
		CLIContext context = new CLIContext();
		CLIPluginContext pluginContext = new CLIPluginContext(context, "test");
		ProMJGraphPanel mainPanel = ProMJGraphVisualizer.instance().visualizeGraph(pluginContext, flex);
     
		mainPanel.setSize(new Dimension(500, 500));	     
 
		ProMJGraph graph = (ProMJGraph)mainPanel.getComponent();
		graph.setSize(new Dimension(500, 500));
	} 
 
	private void removeStrangeDependencies(Graph g, ObjectIntOpenHashMap<String> map, ObjectArrayList<Constraint> vincoli_positivi)
	{
		for (int ii = 0; ii < g.listaNodi().size(); ii++) {
			Node n = (Node)g.listaNodi().get(ii);
			g.removeEdge(n, n);
			n.decr_Outer_degree();
			n.decr_Inner_degree();
			for (int jj = 0; jj < g.adjacentNodes(n).size(); jj++) {
				Node adjNode = (Node)g.listaNodi().get(jj);
         
				if (n.getNomeAttivita().split("_")[1].split("\\+")[0].equals(adjNode.getNomeAttivita().split("_")[0]))
				{
					g.removeEdge(n, adjNode);
					System.out.println("RIMOSSO ARCO " + n.getNomeAttivita() + " -> " + adjNode.getNomeAttivita());
           
					n.decr_Outer_degree();
	     	     adjNode.decr_Inner_degree();
				}
			}
		}
     
		Node pb = new Node("via panebianco_via busento (rende 1o fermata)+complete", 
		map.get("via panebianco_via busento (rende 1o fermata)+complete"));
		Node cmf = new Node("corso mazzini_corso fera (clinica sacro cuore)+complete", 
		map.get("corso mazzini_corso fera (clinica sacro cuore)+complete"));
		g.removeEdge(pb, cmf);
	} 
 
	public boolean[][] generaAdjacentsMatrix(Graph folded_g)
	{
		boolean[][] adjacentsMatrix = new boolean[folded_g.listaNodi().size()][folded_g.listaNodi().size()];
     
		for (int i = 0; i < folded_g.listaNodi().size(); i++) {
			Node n = (Node)folded_g.listaNodi().get(i);
			for (int j = 0; j < folded_g.adjacentNodes(n).size(); j++) {
				Node adjacent = (Node)folded_g.adjacentNodes(n).get(j);
				adjacentsMatrix[n.getID_attivita()][adjacent.getID_attivita()] = true;
			}
		}
		return adjacentsMatrix;
	}
   
	public boolean verifica_consistenza_vincoli(ObjectArrayList<Constraint> vincoli_positivi, ObjectArrayList<Constraint> vincoli_negati)
	{
		for (int i = 0; i < vincoli_positivi.size(); i++) {
			Constraint c = (Constraint)vincoli_positivi.get(i);
			for (int j = 0; j < vincoli_negati.size(); j++) {
	    	   Constraint f = (Constraint)vincoli_negati.get(j);
	    	   if ((c.equals(f)) && (((c.isPathConstraint()) && (f.isPathConstraint())) || ((!c.isPathConstraint()) && (!f.isPathConstraint()))))
	    		   return false;
			}
		}
		return true;
	}
	
	public void aggiungiAttivitaFittizia(XLog xlog)
	{
		XFactory factory = (XFactory)XFactoryRegistry.instance().currentDefault();
     
		for (int i = 0; i < xlog.size(); i++)
		{
			XTrace trace = (XTrace)xlog.get(i);
			XEvent activity_first = (XEvent)trace.get(0);
			XEvent activity_last = (XEvent)trace.get(trace.size() - 1);
			
			XAttribute concept_name = activity_first.getAttributes().get("concept:name");
       
			if (concept_name.equals("_START_")) {
				break;
			}
       
			Date first_activity_ts = XTimeExtension.instance().extractTimestamp(activity_first);
       
			XEvent event_first = factory.createEvent();
       
			XConceptExtension.instance().assignName(event_first, "_START_");
			XLifecycleExtension.instance().assignTransition(event_first, "complete");
       
			if (first_activity_ts != null) {
				XTimeExtension.instance().assignTimestamp(event_first, new Date(first_activity_ts.getTime() - 10L));
			}
 
			trace.add(0, event_first);
	 
			Date last_activity_ts = XTimeExtension.instance().extractTimestamp(activity_last);
	       
			XEvent event_last = factory.createEvent();
	       
			XConceptExtension.instance().assignName(event_last, "_END_");
			XLifecycleExtension.instance().assignTransition(event_last, "complete");
	       
			if (last_activity_ts != null) {
				XTimeExtension.instance().assignTimestamp(event_last, new Date(last_activity_ts.getTime() + 10L));
			}
			trace.add(event_last);
		}
	}
   
	public void bestEdge(Graph unfolded_g, double[][] m, ObjectArrayList<Constraint> lista_vincoli_positivi_unfolded, ObjectArrayList<Constraint> lista_vincoli_positivi_folded, ObjectArrayList<Constraint> vincoli_negati, ObjectArrayList<Forbidden> lista_forbidden, ObjectArrayList<Forbidden> lista_forbidden_unfolded, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double[][] csm, double sigma, Graph folded_g, ObjectIntOpenHashMap<String> folded_map)
	{
		sigma = -100.0D;
		
		for (int i = 0; i < lista_vincoli_positivi_unfolded.size(); i++)
		{
			Constraint vincolo = (Constraint)lista_vincoli_positivi_unfolded.get(i);
       
			if (!vincolo.isPathConstraint())
			{
				String bestBodyNode = "";
				String bestHeadNode = "";
				double bestNodeCS = -1.7976931348623157E308D;

				Iterator localIterator2 = vincolo.getHeadList().iterator(); 
				Iterator localIterator1 = vincolo.getBodyList().iterator();
					
				while(localIterator1.hasNext() && localIterator2.hasNext()){
					String body = (String)localIterator1.next();
					String activity_x = body;
					String head = (String)localIterator2.next();
					String activity_a = head;

					double currentCS = csm[map.get(activity_x)][map.get(activity_a)];
					if (currentCS > bestNodeCS) {
						bestBodyNode = activity_x;
						bestHeadNode = activity_a;
						bestNodeCS = currentCS;
					}
				}
         
				Node x = new Node(bestBodyNode, map.get(bestBodyNode));
				Node a = new Node(bestHeadNode, map.get(bestHeadNode));
         
				if (!unfolded_g.isConnected(x, a))
				{
					if (csm[map.get(bestBodyNode)][map.get(bestHeadNode)] >= sigma) {
						unfolded_g.addEdge(x, a, true);
             
						x.incr_Outer_degree();
						a.incr_Inner_degree();
					}
					else {
						System.out.println("FALLIMENTO!");
						System.out.println("IMPOSSIBILE AGGIUNGERE ARCO " + x.getNomeAttivita() + " => " + 
							a.getNomeAttivita());
					}
				}
			}
		}
	}
 
	public void bestPath(Graph unfolded_g, double[][] m, ObjectArrayList<Constraint> lista_vincoli_positivi_unfolded, ObjectArrayList<Constraint> lista_vincoli_positivi_folded, ObjectArrayList<Constraint> vincoli_negati, ObjectArrayList<Forbidden> lista_forbidden, ObjectArrayList<Forbidden> lista_forbidden_unfolded, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double[][] csm, double sigma, Graph folded_g, ObjectIntOpenHashMap<String> folded_map)
	{
		sigma = -100.0D;
		
		Node x = null;
     
		Node a = null;     
 
		for (int i = 0; i < lista_vincoli_positivi_unfolded.size(); i++)
		{
			Constraint vincolo = (Constraint)lista_vincoli_positivi_unfolded.get(i);
       
			if (vincolo.isPathConstraint())
			{
				String bestBodyNode = "";
				String bestHeadNode = "";
				String bestThroughNode = "";
         
				double bestPathCS = -1.7976931348623157E308D;
         
				Iterator localIterator2 = vincolo.getHeadList().iterator(); 
				Iterator localIterator1 = vincolo.getBodyList().iterator();
					
				while(localIterator1.hasNext() && localIterator2.hasNext()){
					String body = (String)localIterator1.next();
					String activity_x = body;
					bestBodyNode = activity_x;
						         
					String head = (String)localIterator2.next();
					String activity_a = head;
					bestHeadNode = activity_a;
						
					x = new Node(bestBodyNode, map.get(bestBodyNode));
					a = new Node(bestHeadNode, map.get(bestHeadNode));
						
					if (unfolded_g.isConnected(x, a)) {
						break;
					}
					
					for (int ni = 0; ni < unfolded_g.listaNodi().size(); ni++) {
						Node n = (Node)unfolded_g.listaNodi().get(ni);
						n.setMark(false);
					}
					if (bfs(unfolded_g, x, a, null, null)) {
						break;
					}
				}
					
				localIterator2 = vincolo.getHeadList().iterator(); 
				localIterator1 = vincolo.getBodyList().iterator();
					
				while(localIterator1.hasNext() && localIterator2.hasNext()){
					String body = (String)localIterator1.next();
					String activity_x = body;
						
					bestBodyNode = activity_x;
					String head = (String)localIterator2.next();
					String activity_a = head;
					bestHeadNode = activity_a;
						           
					x = new Node(bestBodyNode, map.get(bestBodyNode));
					a = new Node(bestHeadNode, map.get(bestHeadNode));
						           
					boolean[] states = map.allocated;
					Object[] keys = map.keys;
						           
					for (int ii = 0; ii < states.length; ii++)
					{
						if (states[ii] != false) {
							String activity_y = (String)keys[ii];						               
						 
							if ((!activity_x.equals(activity_y)) && (!activity_a.equals(activity_y)) && 
					    		(!lista_forbidden_unfolded.contains(new Forbidden(activity_x, activity_y))) && 
					    		(!lista_forbidden_unfolded.contains(new Forbidden(activity_y, activity_a))) && 
					    		(!activity_y.equals(attivita_iniziale + "#0000")) && 
								(!activity_y.equals(attivita_finale + "#0000")))
							{
								double currentCS = -Math.log(1.1D - csm[map.get(activity_x)][map.get(activity_y)]) - 
										Math.log(1.1D - csm[map.get(activity_y)][map.get(activity_a)]);
						                 
								if (currentCS > bestPathCS)
								{
									bestThroughNode = activity_y;
									bestPathCS = currentCS;
								}
							}
						}
					}
				}
				if (bestThroughNode.equals(""))
				{
					if (lista_forbidden_unfolded.contains(new Forbidden(bestBodyNode, bestHeadNode)))
					{
						System.out.println("Impossibile soddisfare il vincolo " + vincolo);
						System.out.println("Provo con il prossimo set!");
					}
					else if (!unfolded_g.isConnected(x, a))
					{
						if (csm[map.get(bestBodyNode)][map.get(bestHeadNode)] >= sigma) {
							unfolded_g.addEdge(x, a, true);

							x.incr_Outer_degree();
							a.incr_Inner_degree();
						} else {
							System.out.println("FALLIMENTO!");
							System.out.println("IMPOSSIBILE AGGIUNGERE ARCO " + x.getNomeAttivita() + " => " + 
								a.getNomeAttivita()); 
						}
					}
				}
				else
				{
					Node y = new Node(bestThroughNode, map.get(bestThroughNode));
           
					if (!unfolded_g.isConnected(x, a))
					{
						if (csm[map.get(bestBodyNode)][map.get(bestHeadNode)] >= sigma) {
							unfolded_g.addEdge(x, a, true);
 
							x.incr_Outer_degree();
							a.incr_Inner_degree();
						} else {
							System.out.println("FALLIMENTO!");
							System.out.println("IMPOSSIBILE AGGIUNGERE ARCO " + x.getNomeAttivita() + " => " + 
								a.getNomeAttivita());
               
							continue;
						}
					}
           
					if (!unfolded_g.isConnected(x, y))
					{
						if (csm[map.get(bestBodyNode)][map.get(bestThroughNode)] >= sigma) {
							unfolded_g.addEdge(x, y, true);
	                
							x.incr_Outer_degree();
							y.incr_Inner_degree();
						} else {
							System.out.println("FALLIMENTO!");
							System.out.println("IMPOSSIBILE AGGIUNGERE ARCO " + x.getNomeAttivita() + " => " + 
									y.getNomeAttivita());
							continue;
						}
					}
           
					if (!unfolded_g.isConnected(y, a))
					{
						if (csm[map.get(bestThroughNode)][map.get(bestHeadNode)] >= sigma) {
							unfolded_g.addEdge(y, a, true);
               
							y.incr_Outer_degree();
							a.incr_Inner_degree();
						} else {
							System.out.println("FALLIMENTO!");
							System.out.println("IMPOSSIBILE AGGIUNGERE ARCO " + y.getNomeAttivita() + " => " + 
								a.getNomeAttivita());
						}
					}
				}
			}
		}
	}
	
	public void algoritmo2(double[][] m, Graph graph, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double[][] csm, double sigma_1, ObjectIntOpenHashMap<String> folded_map, ObjectArrayList<Forbidden> lista_forbidden, ObjectArrayList<Constraint> vincoli_positivi, ObjectArrayList<Constraint> vincoli_negati)
	{
		ObjectArrayList<FakeDependency> ap_rimosse = new ObjectArrayList<FakeDependency>();
		ap_rimosse.trimToSize();
		int k = 1;
     
		for (;;)
		{
			Graph folded_g = getGrafoAggregato(graph, null, false, folded_map, null, null);
       
			ObjectArrayList<FakeDependency> attivita_parallele = getAttivitaParallele(
				m, graph, map, vincoli_positivi, 
	 	       	folded_map, folded_g
	 	    );
       
			for (int i = 0; i < ap_rimosse.size(); i++) {
				attivita_parallele.removeFirstOccurrence((FakeDependency)ap_rimosse.get(i));
			}
       
			if (attivita_parallele.size() == 0)
			{
				return;
			}
       
			FakeDependency best_ap = null;
       
			double best_causal_score = Double.MAX_VALUE;
			
			for (int i = 0; i < attivita_parallele.size(); i++)
			{
				FakeDependency current_ap = (FakeDependency)attivita_parallele.get(i);
         
				double current_ap_cs = csm[current_ap.getAttivita_x()][current_ap.getAttivita_y()];
         
				if (current_ap_cs < best_causal_score) {
					best_causal_score = current_ap_cs;
					best_ap = current_ap;
				}
			}
 
			Node nx = graph.getNode(getKeyByValue(map, best_ap.getAttivita_x()), best_ap.getAttivita_x());
       
			Node ny = graph.getNode(getKeyByValue(map, best_ap.getAttivita_y()), best_ap.getAttivita_y());
       
			graph.removeEdge(nx, ny);
			m[best_ap.getAttivita_x()][best_ap.getAttivita_y()] = 0.0D;
       
			nx.decr_Outer_degree();
			ny.decr_Inner_degree();
       
			ObjectOpenHashSet<String> lista_candidati_best_pred = null;
       
			lista_candidati_best_pred = bestPred_Folded(
				ny.getID_attivita(), nx.getID_attivita(), map, attivita_tracce, 
				traccia_attivita
			);
 
			String best_pred = attivita_iniziale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
       
			if (lista_candidati_best_pred != null)
			{
				if (lista_candidati_best_pred.size() > 0)
				{
					ObjectArrayList<String> lista_candidati_best_pred_unfolded = new ObjectArrayList<String>();
					Object[] keys = lista_candidati_best_pred.keys;
           
					for (int i = 0; i < lista_candidati_best_pred.allocated.length; i++) {
						if (lista_candidati_best_pred.allocated[i] != false) {
							String activity = (String)keys[i];
							String best_unfolded_item = "";
							double best_unfolded_cs = -1.0D;
               
							keys = map.keys;
							boolean[] values = map.allocated;
               
							for (int j = 0; j < values.length; j++) {
								if (values[j] != false) {
									String unfolded_item = (String)keys[j];
                   
									if (unfolded_item != null)
									{
 
										if ((unfolded_item.split("#")[0].equals(activity)) && 
											(csm[map.get(unfolded_item)][ny.getID_attivita()] > best_unfolded_cs)) 
										{
											best_unfolded_item = unfolded_item;
											best_unfolded_cs = csm[map.get(unfolded_item)][ny.getID_attivita()];
										} 
									}
								}
							}
							lista_candidati_best_pred_unfolded.add(best_unfolded_item);
						}
					}
					
					best_pred = getFinalBestPred(
						graph, csm, ny, map, lista_candidati_best_pred_unfolded, 
						vincoli_negati, lista_forbidden, folded_g, folded_map, false
					);
				}
				else
				{
					System.out.println("FALLIMENTO BEST PRED NON TROVATO!!!");
				}
			}
			ObjectOpenHashSet<String> lista_candidati_best_succ = null;
 
			lista_candidati_best_succ = bestSucc_Folded(
				best_ap.getAttivita_x(), best_ap.getAttivita_y(), map, 
				attivita_tracce, traccia_attivita
			);
       
			String best_succ = attivita_finale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
       
 
			if (lista_candidati_best_succ != null) {
				if (lista_candidati_best_succ.size() > 0)
				{
					ObjectArrayList<String> lista_candidati_best_succ_unfolded = new ObjectArrayList<String>();
					
					Iterator<ObjectCursor<String>> it = lista_candidati_best_succ.iterator();
					while (it.hasNext())
					{
						String activity = (String)((ObjectCursor)it.next()).value;
             
						String best_unfolded_item = "";
						double best_unfolded_cs = -1.0D;
             
						boolean[] states = map.allocated;
             
						Object[] keys = map.keys;
						for (int j = 0; j < states.length; j++)
						{
							if (states[j] != false) {
								String unfolded_item = (String)keys[j];
								if (unfolded_item != null)
								{
									if ((unfolded_item.split("#")[0].equals(activity)) && 
										(csm[nx.getID_attivita()][map.get(unfolded_item)] > best_unfolded_cs)) 
									{
										best_unfolded_item = unfolded_item;
										best_unfolded_cs = csm[nx.getID_attivita()][map.get(unfolded_item)];
									} 
								}
							}
						}
						if (best_unfolded_item.equals("")) {
							System.out.println(activity);
							System.out.println("errore best succ ");
							throw new RuntimeException("ciao");
						}
						lista_candidati_best_succ_unfolded.add(best_unfolded_item);
					} 
					best_succ = getFinalBestSucc(
						graph, csm, nx, map, lista_candidati_best_succ_unfolded, 
						vincoli_negati, lista_forbidden, folded_g, folded_map, false
					);
				}
				else
				{
					System.out.println("FALLIMENTO BEST SUCC NON TROVATO!!!");
				}
			}
			if (!best_pred.equals(""))
			{		
				Node nz = graph.getNode(getKeyByValue(map, map.get(best_pred)), map.get(best_pred));
   
				if (!graph.isConnected(nz, ny))
				{
					m[map.get(best_pred)][best_ap.getAttivita_y()] = 1.0D;
					graph.addEdge(nz, ny, false);
       
					nz.incr_Outer_degree();
					ny.incr_Inner_degree();
				}
			}
			if (!best_succ.equals(""))
			{				
				Node nw = graph.getNode(getKeyByValue(map, map.get(best_succ)), map.get(best_succ));
       
				System.out.println();
				if (!graph.isConnected(nx, nw)) {
					m[best_ap.getAttivita_x()][map.get(best_succ)] = 1.0D;
					graph.addEdge(nx, nw, false);
            
					nx.incr_Outer_degree();
					nw.incr_Inner_degree();
				}
			}       
			ap_rimosse.add(best_ap);
        
			if (graph.isConnected(ny, nx))
			{
				boolean soddisfa_vincoli_positivi = verificaVincoliPositivi(
					folded_g, 
					folded_g.getNode(ny.getNomeAttivita().split("#")[0], 
						folded_map.get(ny.getNomeAttivita().split("#")[0])
					), 
					folded_g.getNode(nx.getNomeAttivita().split("#")[0], 
						folded_map.get(nx.getNomeAttivita().split("#")[0])), vincoli_positivi, folded_map
					);
 
				if (soddisfa_vincoli_positivi) {
					System.out.println();
					FakeDependency best_ap_yx = new FakeDependency(ny.getID_attivita(), nx.getID_attivita());
           
					graph.removeEdge(ny, nx);
					m[best_ap.getAttivita_y()][best_ap.getAttivita_x()] = 0.0D;
           
					ny.decr_Outer_degree();
					nx.decr_Inner_degree();
           
					ObjectOpenHashSet<String> lista_candidati_best_pred_yx = null;
           
					lista_candidati_best_pred_yx = bestPred_Folded(
						nx.getID_attivita(), ny.getID_attivita(), map, 
						attivita_tracce, traccia_attivita
					);
            
					String best_pred_yx = attivita_iniziale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
            
					if (lista_candidati_best_pred_yx != null)
					{
						if (lista_candidati_best_pred_yx.size() > 0)
						{
							ObjectArrayList<String> lista_candidati_best_pred_unfolded = new ObjectArrayList<String>();
               
							Iterator<ObjectCursor<String>> it = lista_candidati_best_pred_yx.iterator();
							while (it.hasNext())
							{ 
								String activity = (String)((ObjectCursor)it.next()).value;
								String best_unfolded_item = "";
								double best_unfolded_cs = -1.0D;
                 
								boolean[] states = map.allocated;
								Object[] keys = map.keys;
								
								for (int j = 0; j < states.length; j++) {
									if (states[j] != false) {
										String unfolded_item = (String)keys[j];
										if (unfolded_item != null)
										{
											if ((unfolded_item.split("#")[0].equals(activity)) && 
												(csm[map.get(unfolded_item)][nx.getID_attivita()] > best_unfolded_cs)) 
											{
												best_unfolded_item = unfolded_item;
												best_unfolded_cs = csm[map.get(unfolded_item)][nx.getID_attivita()];
											} 
										}
									}
								}
								if (!best_unfolded_item.equals("")) {
									lista_candidati_best_pred_unfolded.add(best_unfolded_item);
								}
							} 
							best_pred_yx = getFinalBestPred(
								graph, csm, nx, map, lista_candidati_best_pred_unfolded, 
								vincoli_negati, lista_forbidden, folded_g, folded_map, false
							);
						}
						else {
							System.out.println("FALLIMENTO BEST PRED YX NON TROVATO!!!");
						}
					}
					ObjectOpenHashSet<String> lista_candidati_best_succ_yx = null;
					
					lista_candidati_best_succ_yx = bestSucc_Folded(
						best_ap.getAttivita_y(), best_ap.getAttivita_x(), 
						map, attivita_tracce, traccia_attivita
					);
					
					String best_succ_yx = attivita_finale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
            
					if (lista_candidati_best_succ_yx != null) {
						if (lista_candidati_best_succ_yx.size() > 0)
						{
							ObjectArrayList<String> lista_candidati_best_succ_unfolded = new ObjectArrayList<String>();
                
							Iterator<ObjectCursor<String>> it = lista_candidati_best_succ.iterator();
							while (it.hasNext())
							{
								String activity = (String)((ObjectCursor)it.next()).value;
								String best_unfolded_item = "";
								double best_unfolded_cs = -1.0D;
                 
								Object[] keys = map.keys;
                 
								boolean[] states = map.allocated;
                 
								for (int j = 0; j < states.length; j++)
								{
									if (states[j] != false) {
										String unfolded_item = (String)keys[j];
                     
										if (unfolded_item != null)
										{
 
											if ((unfolded_item.split("#")[0].equals(activity)) && 
												(csm[ny.getID_attivita()][map.get(unfolded_item)] > best_unfolded_cs)) 
											{
												best_unfolded_item = unfolded_item;
												best_unfolded_cs = csm[ny.getID_attivita()][map.get(unfolded_item)];
											} 
										}
									}
								}
								if (!best_unfolded_item.equals("")) {
									lista_candidati_best_succ_unfolded.add(best_unfolded_item);
								}
							}
							best_succ_yx = getFinalBestSucc(
								graph, csm, ny, map, lista_candidati_best_succ_unfolded, 
								vincoli_negati, lista_forbidden, folded_g, folded_map, false
							);
						}
						else {
							System.out.println("FALLIMENTO BEST SUCC YX NON TROVATO!!!");
						}
					}
					if (!best_pred_yx.equals(""))
					{
						Node nz = graph.getNode(getKeyByValue(map, map.get(best_pred_yx)), map.get(best_pred_yx));
             
						if (!graph.isConnected(nz, nx))
						{
							m[map.get(best_pred_yx)][best_ap.getAttivita_x()] = 1.0D;
							graph.addEdge(nz, nx, false);               
 
							nz.incr_Outer_degree();
							nx.incr_Inner_degree();
						}
					}
					if (!best_succ_yx.equals(""))
					{
						Node nw = graph.getNode(getKeyByValue(map, map.get(best_succ_yx)), map.get(best_succ_yx));
             
						if (!graph.isConnected(ny, nw)) {
							m[best_ap.getAttivita_y()][map.get(best_succ)] = 1.0D;
							graph.addEdge(ny, nw, false);
                
							ny.incr_Outer_degree();
							nw.incr_Inner_degree();
						}
					}
					ap_rimosse.add(best_ap_yx);
				}
			}
		}
	}
	
	public ObjectOpenHashSet<String> bestPred_Folded(int x, int y, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita)
	{
		String attivita_x = getKeyByValue(map, x);
     
		String attivita_y = getKeyByValue(map, y);
     
		ObjectArrayList<String> lista_tracce_x = new ObjectArrayList((ObjectContainer)attivita_tracce.get(attivita_x));
     
		ObjectOpenHashSet<String> lista_tracce_y = new ObjectOpenHashSet((ObjectContainer)attivita_tracce.get(attivita_y));
     
		lista_tracce_x.retainAll(lista_tracce_y); 
 
		ObjectOpenHashSet<String> attivita_candidate = null;
     
		String trace_1 = "";
     
		if (lista_tracce_x.size() > 0)
		{
			trace_1 = (String)lista_tracce_x.get(0);
			attivita_candidate = getPredecessors_FoldedLocal(trace_1, attivita_x, attivita_y, traccia_attivita);
		} else {
			attivita_candidate = new ObjectOpenHashSet<String>();
			attivita_candidate.add(attivita_iniziale);
		}
 
		for (int i = 1; i < lista_tracce_x.size(); i++)
		{
			String trace = (String)lista_tracce_x.get(i);
       
			ObjectOpenHashSet<String> predecessors = getPredecessors_FoldedLocal(trace, attivita_x, attivita_y, traccia_attivita);
       
			attivita_candidate.retainAll(predecessors);
		}     
 
		return attivita_candidate;
	}
 
	public ObjectOpenHashSet<String> bestSucc_Folded(int x, int y, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita)
	{
		String attivita_x = getKeyByValue(map, x);
     
		String attivita_y = getKeyByValue(map, y);
     
		ObjectArrayList<String> lista_tracce_x = new ObjectArrayList((ObjectContainer)attivita_tracce.get(attivita_x));
     
		ObjectOpenHashSet<String> lista_tracce_y = new ObjectOpenHashSet((ObjectContainer)attivita_tracce.get(attivita_y));
     
		lista_tracce_x.retainAll(lista_tracce_y);
     
		ObjectOpenHashSet<String> attivita_candidate = null;
     
		String trace_1 = "";
     
		if (lista_tracce_x.size() > 0)
		{
			trace_1 = (String)lista_tracce_x.get(0);
			attivita_candidate = getSuccessors_FoldedLocal(trace_1, attivita_x, attivita_y, traccia_attivita);
		} else {
			attivita_candidate = new ObjectOpenHashSet<String>();
			attivita_candidate.add(attivita_finale);
		}
 
		int i = 1;
		while (i < lista_tracce_x.size())
		{
			String trace = (String)lista_tracce_x.get(i);
       
			ObjectOpenHashSet<String> successors = getSuccessors_FoldedLocal(trace, attivita_x, attivita_y, traccia_attivita);
       
			attivita_candidate.retainAll(successors);
			i++;
		}
     
		return attivita_candidate;
	}
 
	private boolean bfs(Graph graph, Node x, Node y, Node f, ObjectArrayList<Node> path)
	{
		boolean atLeastOnePath = false;
     
		if (x.equals(y)) {
			if (graph.isConnected(x, y))
				return true;
			if (path == null)
		        path = new ObjectArrayList<Node>();
		}
		ObjectArrayList<Node> nodes = new ObjectArrayList<Node>();
		nodes.add(x);
		x.setMark(true);
		Node t;

		int i = 0;
		do
		{
			t = (Node)nodes.remove(0);
			if (path != null) {
				path.add(t);
			}
			if (t.equals(y)) {
				if (x.equals(y)) {
					if (path.size() > 1) {
						atLeastOnePath = true;
					}
				} else {
				atLeastOnePath = true;
				}
			}
			Node k = (Node)graph.adjacentNodes(t).get(i);
			if ((!k.isMarked()) && (!k.equals(f))) {
				k.setMark(true);
				nodes.add(k);
			}
			i++;
		}
		while(!nodes.isEmpty() && i < graph.adjacentNodes(t).size());
 
		return atLeastOnePath;
	}
 
	public double[][] buildNextMatrix(XLog log, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita)
	{
		double[][] mNext = new double[map.size()][map.size()];
     
		Object[] values = traccia_attivita.values;
     
		for (int i = 0; i < traccia_attivita.allocated.length; i++) {
			if (traccia_attivita.allocated[i] != false)
			{
				ObjectArrayList<String> value = (ObjectArrayList)values[i];
         
				String activity_x = "";
				if (value.size() > 0) {
					activity_x = (String)value.get(0);
				}
				int j = 1;
				while (j < value.size())
				{ 
					String activity_y = (String)value.get(j);
            
					int x = map.get(activity_x);
					int y = map.get(activity_y);
					mNext[x][y] += 1.0D;
		           
					activity_x = activity_y;
					j++;
				}
			}
		}
     
		return mNext;
	}
	
	public double[][] buildBestNextMatrix(XLog log, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double[][] cs, ObjectArrayList<Forbidden> lista_forbidden_unfolded)
	{
		double[][] mNext = new double[map.size()][map.size()];
     
		Object[] values = traccia_attivita.values;
		for (int i = 0; i < traccia_attivita.allocated.length; i++) {
			if (traccia_attivita.allocated[i] != false)
			{
				ObjectArrayList<String> value = (ObjectArrayList)values[i];
          
				ObjectArrayList<String> predecessors = new ObjectArrayList<String>();
				ObjectArrayList<String> successors = new ObjectArrayList<String>(value);
         
				int count = 0;
 
				int j = 0;
				while (j < value.size())
				{					
					String activity_x = (String)value.get(j);
           
					successors.removeFirstOccurrence(activity_x);
           
					String bestPred = "";
           
					String bestSucc = "";
           
					double bestPredCS = Double.MIN_VALUE;
					double bestSuccCS = Double.MIN_VALUE;
           
					if (predecessors.size() > 0)
					{
						int itPred = 0;
						Object[] buffer = predecessors.buffer;
						while (itPred < predecessors.size()) {
							String pred = (String)buffer[itPred];
               
							double predCS = cs[map.get(pred)][map.get(activity_x)];
               
							if ((predCS > bestPredCS) && (!lista_forbidden_unfolded.contains(new Forbidden(pred, activity_x)))) {
								bestPred = pred;
								bestPredCS = predCS;
							}
							itPred++;
						}
             
						int x = map.get(bestPred);
						int y = map.get(activity_x);
						mNext[x][y] += 1.0D;
					}
           
					if (successors.size() > 0)
					{
						int itSucc = 0;
						Object[] buffer = successors.buffer;
						while (itSucc < successors.size()) {
							String succ = (String)buffer[itSucc];
							double succCS = cs[map.get(activity_x)][map.get(succ)];
               
							if ((succCS > bestSuccCS) && (!lista_forbidden_unfolded.contains(new Forbidden(activity_x, succ)))) {
								bestSucc = succ;
								bestSuccCS = succCS;
							}
							itSucc++;
						}             
						int x = map.get(activity_x);
						int y = map.get(bestSucc);
             
						mNext[x][y] += 1.0D;
					}
           
					predecessors.add(activity_x);
					j++;
				}
			}
		}
     
		return mNext;
	}
	
	public void buildPG0(Graph unfolded_g, double[][] m, ObjectArrayList<Constraint> lista_vincoli_positivi_unfolded, ObjectArrayList<Constraint> lista_vincoli_positivi_folded, ObjectArrayList<Constraint> vincoli_negati_unfolded, ObjectArrayList<Constraint> vincoli_negati_folded, ObjectArrayList<Forbidden> lista_forbidden, ObjectArrayList<Forbidden> lista_forbidden_unfolded, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double[][] csm, double sigma, Graph folded_g, ObjectIntOpenHashMap<String> folded_map)
	{
		boolean flag = false;
     
		if (lista_vincoli_positivi_folded.size() == 0) {
	 	     flag = true;
	 	     Object[] buffer = vincoli_negati_folded.buffer;
	 	     for (int i = 0; i < vincoli_negati_folded.size(); i++) {
	 	    	 Constraint c = (Constraint)buffer[i];
	 	    	 if (!c.isPathConstraint()) {
	 	    		 flag = false;
	 	    		 break;
	 	    	 }
	 	     }
		}
     
		if (!flag)
		{
 
			bestEdge(unfolded_g, m, lista_vincoli_positivi_unfolded, lista_vincoli_positivi_folded, vincoli_negati_folded, 
				lista_forbidden, lista_forbidden_unfolded, map, attivita_tracce, traccia_attivita, csm, sigma, 
				folded_g, folded_map);
       
			bestPath(unfolded_g, m, lista_vincoli_positivi_unfolded, lista_vincoli_positivi_folded, vincoli_negati_folded, 
				lista_forbidden, lista_forbidden_unfolded, map, attivita_tracce, traccia_attivita, csm, sigma, 
				folded_g, folded_map);
        
			eliminaForbidden(unfolded_g, lista_forbidden_unfolded, lista_forbidden, map, m, csm, attivita_tracce, 
				traccia_attivita, lista_vincoli_positivi_folded, vincoli_negati_folded, folded_g, folded_map);
		}
		else
		{
			System.out.println("SECONDO ALGORITMO ");
			noPathConstraints(unfolded_g, m, lista_vincoli_positivi_unfolded, lista_vincoli_positivi_folded, vincoli_negati_unfolded, vincoli_negati_folded, 
				lista_forbidden, lista_forbidden_unfolded, map, attivita_tracce, traccia_attivita, csm, sigma, 
				folded_g, folded_map);
		}
	}
	
	public void noPathConstraints(Graph unfolded_g, double[][] m, ObjectArrayList<Constraint> lista_vincoli_positivi_unfolded, ObjectArrayList<Constraint> lista_vincoli_positivi_folded, ObjectArrayList<Constraint> vincoli_negati_unfolded, ObjectArrayList<Constraint> vincoli_negati, ObjectArrayList<Forbidden> lista_forbidden, ObjectArrayList<Forbidden> lista_forbidden_unfolded, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double[][] csm, double sigma, Graph folded_g, ObjectIntOpenHashMap<String> folded_map)
  	{
		Object[] buffer = lista_forbidden_unfolded.buffer;
		for (int k = 0; k < lista_forbidden_unfolded.size(); k++)
		{
			Forbidden f = (Forbidden)buffer[k];
       
			Node x = new Node(f.getB(), map.get(f.getB()));
			Node y = new Node(f.getA(), map.get(f.getA()));
       
			if (unfolded_g.isConnected(x, y)) {
				unfolded_g.removeEdge(x, y);
			}
			for (int ni = 0; ni < unfolded_g.listaNodi().size(); ni++) {
				Node n = (Node)unfolded_g.listaNodi().get(ni);
				n.setMark(false);
			}
       
			ObjectArrayList<Node> listaNodiPath = new ObjectArrayList<Node>();
       
			boolean spezzaPath = bfs(unfolded_g, x, y, null, listaNodiPath);
        
			if (spezzaPath)
			{
				
				ObjectArrayList<Edge> archiRimossi = new ObjectArrayList<Edge>();
				
				do
				{
					double minCs = Double.MAX_VALUE;
           
					Node z = null;
					Node w = null;
					Node zz = null;
					Node ww = null;
           
					for (int i = 0; i < listaNodiPath.size() - 1; i++) {
						for (int j = i + 1; j < listaNodiPath.size(); j++)
						{
							zz = (Node)listaNodiPath.get(i);
							ww = (Node)listaNodiPath.get(j);
							Edge e = new Edge(zz, ww);
               
							if (unfolded_g.getLista_archi().contains(e))
							{ 
								if ((!archiRimossi.contains(e)) && (csm[zz.getID_attivita()][ww.getID_attivita()] < minCs)) {
									minCs = csm[zz.getID_attivita()][ww.getID_attivita()];
									z = zz;
									w = ww;
								} 
							}
						}
					}
					archiRimossi.add(new Edge(z, w));
           
					unfolded_g.removeEdge(z, w);
           
					System.out.println("RIMOSSO ARCO FORBIDDEN " + z.getNomeAttivita() + " => " + w.getNomeAttivita());
					m[z.getID_attivita()][w.getID_attivita()] = 0.0D;
           
					z.decr_Outer_degree();
					w.decr_Inner_degree();
	           
					ObjectOpenHashSet<String> lista_candidati_best_pred = null;
            
					lista_candidati_best_pred = bestPred_Folded(w.getID_attivita(), z.getID_attivita(), map, attivita_tracce, traccia_attivita);
           
					String best_pred = attivita_iniziale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
            
					if (lista_candidati_best_pred != null)
					{
						if (lista_candidati_best_pred.size() > 0)
						{
							ObjectArrayList<String> lista_candidati_best_pred_unfolded = new ObjectArrayList();
               
							Iterator<ObjectCursor<String>> it = lista_candidati_best_pred.iterator();
							while (it.hasNext())
							{
								String activity = (String)((ObjectCursor)it.next()).value;
                 
								String best_unfolded_item = "";
								double best_unfolded_cs = -1.0D;
                 
								Object[] keys2 = map.keys;
								for (int j = 0; j < map.allocated.length; j++) {
									if (map.allocated[j] != false)
									{
										String unfolded_item = (String)keys2[j];
                     
										if ((unfolded_item.split("#")[0].equals(activity)) && 
											(csm[map.get(unfolded_item)][w.getID_attivita()] > best_unfolded_cs)) {
											best_unfolded_item = unfolded_item;
											best_unfolded_cs = csm[map.get(unfolded_item)][w.getID_attivita()];
										}
									}
								}
								lista_candidati_best_pred_unfolded.add(best_unfolded_item);
							}               
							best_pred = getFinalBestPred(unfolded_g, csm, w, map, lista_candidati_best_pred_unfolded, 
								vincoli_negati, lista_forbidden, folded_g, folded_map, true);
						}
					} 
					ObjectOpenHashSet<String> lista_candidati_best_succ = null;
           
					lista_candidati_best_succ = bestSucc_Folded(z.getID_attivita(), w.getID_attivita(), map, 
						attivita_tracce, traccia_attivita);
           
					String best_succ = attivita_finale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
            
					if ((lista_candidati_best_succ != null) && 
						(lista_candidati_best_succ.size() > 0))
					{
						ObjectArrayList<String> lista_candidati_best_succ_unfolded = new ObjectArrayList<String>();
           
						Iterator<ObjectCursor<String>> it = lista_candidati_best_succ.iterator();
						while (it.hasNext())
						{
							String activity = (String)((ObjectCursor)it.next()).value;
							String best_unfolded_item = "";
							double best_unfolded_cs = -1.0D;
               
							Object[] keys2 = map.keys;
							for (int j = 0; j < map.allocated.length; j++) {
								if (map.allocated[j] != false)
								{
									String unfolded_item = (String)keys2[j];
									if ((unfolded_item.split("#")[0].equals(activity)) && 
										(csm[z.getID_attivita()][map.get(unfolded_item)] > best_unfolded_cs)) {
										best_unfolded_item = unfolded_item;
										best_unfolded_cs = csm[z.getID_attivita()][map.get(unfolded_item)];
									}
								}
							}
							lista_candidati_best_succ_unfolded.add(best_unfolded_item);
						}
						best_succ = getFinalBestSucc(unfolded_g, csm, z, map, lista_candidati_best_succ_unfolded, 
							vincoli_negati, lista_forbidden, folded_g, folded_map, true);
					}
					if (!best_pred.equals(""))
					{
						Node nz = unfolded_g.getNode(getKeyByValue(map, map.get(best_pred)), map.get(best_pred));
             
						if (!unfolded_g.isConnected(nz, w))
						{
							m[map.get(best_pred)][w.getID_attivita()] = 1.0D;
							unfolded_g.addEdge(nz, w, false);
							
							nz.incr_Outer_degree();
							w.incr_Inner_degree();
						}
					}           
					if (!best_succ.equals(""))
					{ 
						Node nw = unfolded_g.getNode(getKeyByValue(map, map.get(best_succ)), map.get(best_succ));
             
						if (!unfolded_g.isConnected(z, nw)) {
	            	  m[z.getID_attivita()][map.get(best_succ)] = 1.0D;
	            	  unfolded_g.addEdge(z, nw, false);
                
	            	  z.incr_Outer_degree();
	            	  nw.incr_Inner_degree();
						}
					}
           
					for (int ni = 0; ni < unfolded_g.listaNodi().size(); ni++) {
						Node n = (Node)unfolded_g.listaNodi().get(ni);
						n.setMark(false);
					}        
				}
				while (bfs(unfolded_g, x, y, null, null));
			}
		}
  	}
	
	public double[][] calcoloMatriceDeiCausalScore(XLog log, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double ff)
  	{
		ObjectArrayList<IntArrayList> vlog = new ObjectArrayList<IntArrayList>();
     
		Object[] values = traccia_attivita.values;
		ObjectCursor<String> s;
		for (int i = 0; i < traccia_attivita.allocated.length; i++)
		{
			if (traccia_attivita.allocated[i] != false)
			{
				IntArrayList t1 = new IntArrayList();
				ObjectArrayList<String> vals = (ObjectArrayList)values[i];
         
				for (Iterator localIterator = vals.iterator(); localIterator.hasNext();) { s = (ObjectCursor)localIterator.next();
					t1.add(map.get((String)s.value)); 
				}
				vlog.add(t1);
			}
		}
     
		double[][] weightMatrix = null;
     
		try
		{
			WeightEstimator.CLOSEST_OCCURRENCE_ONLY = true;
			WeightEstimator weightEstimator = new WeightEstimator(map.size(), -1, ff, 1);
       
			for (ObjectCursor<IntArrayList> t : vlog) {
				weightEstimator.addTraceContribution((IntArrayList)t.value);
			}
       
			weightEstimator.computeWeigths();
			weightMatrix = weightEstimator.getDependencyMatrix();
 
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return weightMatrix;
  	}
    
	public void creaVincoliUnfolded(ObjectArrayList<Constraint> vincoli_positivi, ObjectArrayList<Constraint> vincoli_negati, ObjectArrayList<Forbidden> lista_forbidden, ObjectArrayList<Constraint> vincoli_positivi_unfolded, ObjectArrayList<Constraint> vincoli_negati_unfolded, ObjectArrayList<Forbidden> lista_forbidden_unfolded, ObjectIntOpenHashMap<String> map)
	{
		int i = 0;
		
		Iterator localIterator = vincoli_positivi.iterator();
		while(localIterator.hasNext() && i < map.allocated.length){
			ObjectCursor<Constraint> c = (ObjectCursor)localIterator.next();
			       
			Object[] keys = map.keys;
			if (map.allocated[i] != false) 
			{
				String unfolded_head = (String)keys[i];
					         
				if (((Constraint)c.value).getHeadList().contains(unfolded_head.split("#")[0]))
				{
					Constraint unfolded_c = new Constraint();
					           
					unfolded_c.setConstraintType(((Constraint)c.value).isPositiveConstraint());
					unfolded_c.setPathConstraint(((Constraint)c.value).isPathConstraint());
					unfolded_c.addHead(unfolded_head);
					           
					for (int j = 0; j < map.allocated.length; j++) {
						if (map.allocated[j] != false) {
							String unfolded_body = (String)keys[j];
							if (((Constraint)c.value).getBodyList().contains(unfolded_body.split("#")[0]))
								unfolded_c.addBody(unfolded_body);
						}
		           	}
					 vincoli_positivi_unfolded.add(unfolded_c);
		         }
			}
			i++;
		
		}
		i = 0;
		
		localIterator = vincoli_negati.iterator();
		while(localIterator.hasNext() && i < map.allocated.length)
		{
			ObjectCursor<Constraint> c = (ObjectCursor)localIterator.next();
					 
			Object[] keys2 = map.keys;
					       
			if (map.allocated[i] != false) {
				String unfolded_head = (String)keys2[i];
				if (((Constraint)c.value).getHeadList().contains(unfolded_head.split("#")[0]))
				{
					Constraint unfolded_c = new Constraint();
				           
					unfolded_c.setConstraintType(((Constraint)c.value).isPositiveConstraint());
					unfolded_c.setPathConstraint(((Constraint)c.value).isPathConstraint());
					unfolded_c.addHead(unfolded_head);
				           
					for (int j = 0; j < map.allocated.length; j++) {
						if (map.allocated[j] != false) {
							String unfolded_body = (String)keys2[j];
				               
							if (((Constraint)c.value).getBodyList().contains(unfolded_body.split("#")[0])) {
								unfolded_c.addBody(unfolded_body);
								lista_forbidden_unfolded.add(new Forbidden(unfolded_body, unfolded_head));
							}
				       	}
					}
					vincoli_negati_unfolded.add(unfolded_c);
				}
	       	}
			i++;
		}     
	} 
 
	public void eliminaForbidden(Graph g, ObjectArrayList<Forbidden> lista_forbidden_unfolded, ObjectArrayList<Forbidden> lista_forbidden, ObjectIntOpenHashMap<String> map, double[][] m, double[][] csm, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, ObjectArrayList<Constraint> vincoli_positivi, ObjectArrayList<Constraint> vincoli_negati, Graph folded_g, ObjectIntOpenHashMap<String> folded_map)
	{
		int it = 0;
     
		while (it < lista_forbidden_unfolded.size())
		{
			Forbidden f = (Forbidden)lista_forbidden_unfolded.get(it);
       
			Node x = new Node(f.getB(), map.get(f.getB()));
			Node y = new Node(f.getA(), map.get(f.getA()));
       
			if (g.isConnected(x, y))
			{
				boolean vincoli_soddisfatti = verificaVincoliPositivi(
					folded_g, 
					folded_g.getNode(x.getNomeAttivita().split("#")[0], 
						folded_map.get(x.getNomeAttivita().split("#")[0])), 
					folded_g.getNode(y.getNomeAttivita().split("#")[0], 
						folded_map.get(y.getNomeAttivita().split("#")[0])), vincoli_positivi, folded_map);
         
				if (vincoli_soddisfatti)
				{
					g.removeEdge(x, y);
           
					m[x.getID_attivita()][y.getID_attivita()] = 0.0D;
					System.out.println("RIMOSSO ARCO FORBIDDEN " + x.getNomeAttivita() + " => " + y.getNomeAttivita());
					x.decr_Outer_degree();
					y.decr_Inner_degree();
           
					ObjectOpenHashSet<String> lista_candidati_best_pred = null;
            
					lista_candidati_best_pred = bestPred_Folded(y.getID_attivita(), x.getID_attivita(), map, 
						attivita_tracce, traccia_attivita);
           
					String best_pred = attivita_iniziale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
           
					String best_unfolded_item;
					if (lista_candidati_best_pred != null)
					{
						if (lista_candidati_best_pred.size() > 0)
						{
							ObjectArrayList<String> lista_candidati_best_pred_unfolded = new ObjectArrayList<String>();
               
							for (ObjectCursor<String> activityCursor : lista_candidati_best_pred)
							{
								String activity = (String)activityCursor.value;
								best_unfolded_item = "";
								double best_unfolded_cs = -1.0D;
                 
								Object[] keys = map.keys;
                 
								for (int i = 0; i < map.allocated.length; i++) {
									if (map.allocated[i] != false) {
										String unfolded_item = (String)keys[i];
										if ((unfolded_item.split("#")[0].equals(activity)) && 
											(csm[map.get(unfolded_item)][y.getID_attivita()] > best_unfolded_cs)) {
											best_unfolded_item = unfolded_item;
											best_unfolded_cs = csm[map.get(unfolded_item)][y.getID_attivita()];
										}
									}
								}                 
								lista_candidati_best_pred_unfolded.add(best_unfolded_item);
							} 
							best_pred = getFinalBestPred(g, csm, y, map, lista_candidati_best_pred_unfolded, 
								vincoli_negati, lista_forbidden, folded_g, folded_map, false);
						}
						else {
							System.out.println("FALLIMENTO BEST PRED NON TROVATO!!!");
						}
					} 
					ObjectOpenHashSet<String> lista_candidati_best_succ = null;
           
					lista_candidati_best_succ = bestSucc_Folded(x.getID_attivita(), y.getID_attivita(), map, 
						attivita_tracce, traccia_attivita);
           
					String best_succ = attivita_finale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
           
 
					if (lista_candidati_best_succ != null) {
						if (lista_candidati_best_succ.size() > 0)
						{
							Object lista_candidati_best_succ_unfolded = new ObjectArrayList<Object>();
               
							for (ObjectCursor<String> activityCursor : lista_candidati_best_succ) {
								String activity = (String)activityCursor.value;
								best_unfolded_item = "";
								double best_unfolded_cs = -1.0D;
                 
								Object[] keys = map.keys;
                 
								for (int i = 0; i < map.allocated.length; i++) {
									if (map.allocated[i] != false) {
										String unfolded_item = (String)keys[i];
                     
										if ((unfolded_item.split("#")[0].equals(activity)) && 
											(csm[x.getID_attivita()][map.get(unfolded_item)] > best_unfolded_cs)) {
											best_unfolded_item = unfolded_item;
											best_unfolded_cs = csm[x.getID_attivita()][map.get(unfolded_item)];
										}
									}
								}
								((ObjectArrayList)lista_candidati_best_succ_unfolded).add(best_unfolded_item);
							}
               
							best_succ = getFinalBestSucc(g, csm, x, map, (ObjectArrayList)lista_candidati_best_succ_unfolded, 
								vincoli_negati, lista_forbidden, folded_g, folded_map, false);
						}
						else {
							System.out.println("FALLIMENTO BEST SUCC NON TROVATO!!!");
						}
					}
            
					if (!best_pred.equals(""))
					{
						Node nz = g.getNode(getKeyByValue(map, map.get(best_pred)), map.get(best_pred));
              
						if (!g.isConnected(nz, y))
						{
							m[map.get(best_pred)][y.getID_attivita()] = 1.0D;
							g.addEdge(nz, y, false);
                
							nz.incr_Outer_degree();
							y.incr_Inner_degree();
						}
					}
            
					if (!best_succ.equals(""))
					{ 
						Node nw = g.getNode(getKeyByValue(map, map.get(best_succ)), map.get(best_succ));
             
						if (!g.isConnected(x, nw)) {
							m[x.getID_attivita()][map.get(best_succ)] = 1.0D;
							g.addEdge(x, nw, false);
                
							x.incr_Outer_degree();
							nw.incr_Inner_degree();
						}
					}
				}
			}       
			it++;
		}
	}
	
	private boolean esisteAttivatore(String trace, String activity_x, String activity_y, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, ObjectOpenHashSet<String> candidati_z, boolean flag, boolean autoanello_y, boolean forward)
	{
		ObjectArrayList<String> attivatore_traccia = new ObjectArrayList<String>();
     
		int iter;
		if (!forward) {
			iter = ((ObjectArrayList)traccia_attivita.get(trace)).size() - 1;
		}
		else {
			iter = 0;
		}
		
		boolean trovata_y = false;
     
		while (((iter >= 0) && (!forward)) || ((iter < ((ObjectArrayList)traccia_attivita.get(trace)).size()) && (forward)))
		{
			String activity_z = (String)((ObjectArrayList)traccia_attivita.get(trace)).get(iter);
       
			if ((!trovata_y) && (!activity_z.equals(activity_y)))
			{
				if (!forward) {
					iter--;
				} else {
	  	        iter++;
				}
			}
			else
			{
				if (!trovata_y)
				{
					trovata_y = true;
					if (!forward) {
						iter--;
					} else
						iter++;
					if (((iter >= 0) && (!forward)) || ((iter < ((ObjectArrayList)traccia_attivita.get(trace)).size()) && (forward))) {
						activity_z = (String)((ObjectArrayList)traccia_attivita.get(trace)).get(iter);
					}
				}
				if (flag)
				{
					if (!activity_z.equals(activity_x))
					{
						if (!attivatore_traccia.contains(activity_z)) {
							attivatore_traccia.add(activity_z);
						}
						if (activity_z.equals(activity_y)) {
							attivatore_traccia = new ObjectArrayList<String>();
						}
					}
					else
					{
						attivatore_traccia.retainAll(candidati_z);
             
						if (attivatore_traccia.size() == 0) {
							return false;
						}
						trovata_y = false;
						attivatore_traccia = new ObjectArrayList<String>();
					}
				}
				else if (!activity_z.equals(activity_y))
				{
					if (!attivatore_traccia.contains(activity_z)) {
						attivatore_traccia.add(activity_z);
					}
				}
				else {
					attivatore_traccia.retainAll(candidati_z);
           
					if ((attivatore_traccia.size() == 0) && (!autoanello_y)) {
						return false;
					}   
					attivatore_traccia = new ObjectArrayList<String>();
				}        
				if (!forward) {
					iter--;
				} else
					iter++;
			}
		}
		if (!flag)
		{
			attivatore_traccia.retainAll(candidati_z);
			
			if (attivatore_traccia.size() == 0) {
	    	   return false;
			}
		}
		return true;
	}
	
	public boolean follows(String activity_x, String activity_y, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, double sigma_2)
	{
		ObjectOpenHashSet<String> tracce_n = new ObjectOpenHashSet((ObjectContainer)attivita_tracce.get(activity_x));
		ObjectArrayList<String> tracce_adj = new ObjectArrayList((ObjectContainer)attivita_tracce.get(activity_y));
     
		tracce_adj.retainAll(tracce_n);
     
		int counter = 0;
     
		for (ObjectCursor<String> trace : tracce_adj)
		{
			int it = ((ObjectArrayList)traccia_attivita.get((String)trace.value)).size() - 1;
       
			while (it >= 0)
			{
				String attivita_k = (String)((ObjectArrayList)traccia_attivita.get((String)trace.value)).get(it);
          
				if (attivita_k.equals(activity_x))
				{ 
					counter++;
           
					if (counter > sigma_2 * tracce_adj.size())
					{
						return true;
					}
				} else {
					if (attivita_k.equals(activity_y)) {
						break;
					}
				}         
				it--;
			}
		}
		return false;
	}   
 
	public ObjectArrayList<FakeDependency> getAttivitaParallele(double[][] m, Graph graph, ObjectIntOpenHashMap<String> map, ObjectArrayList<Constraint> vincoli_positivi, ObjectIntOpenHashMap<String> folded_map, Graph folded_g)
	{
		ObjectArrayList<FakeDependency> lista_attivita_parallele = new ObjectArrayList<FakeDependency>();
    
		Iterator localIterator2;
		Iterator localIterator1 = graph.listaNodi().iterator();
 				
		do
		{
			ObjectCursor<Node> np = (ObjectCursor)localIterator1.next();
					       
			localIterator2 = graph.adjacentNodes((Node)np.value).iterator(); 
			ObjectCursor<Node> nr = (ObjectCursor)localIterator2.next();
					       
			boolean b = bfs(graph, (Node)nr.value, (Node)np.value, null, null);
			if (b)
			{					 
				boolean vincoli_soddisfatti = verificaVincoliPositivi(
					folded_g, 
					folded_g.getNode(((Node)np.value).getNomeAttivita().split("#")[0], 
					folded_map.get(((Node)np.value).getNomeAttivita().split("#")[0])), 
					folded_g.getNode(((Node)nr.value).getNomeAttivita().split("#")[0], 
					folded_map.get(((Node)nr.value).getNomeAttivita().split("#")[0])), vincoli_positivi, folded_map);
					         
					if (vincoli_soddisfatti)
					{
						lista_attivita_parallele.add(new FakeDependency(((Node)np.value).getID_attivita(), ((Node)nr.value).getID_attivita()));
					}
			}
					       
			for (int ni = 0; ni < graph.listaNodi().size(); ni++) {
				Node n = (Node)graph.listaNodi().get(ni);
				n.setMark(false);
			}
		}
		while(localIterator1.hasNext() && localIterator2.hasNext());

		return lista_attivita_parallele;
	} 
 
	private String getFinalBestPred(Graph graph, double[][] csm, Node ny, ObjectIntOpenHashMap<String> map, ObjectArrayList<String> lista_candidati_best_pred_unfolded, ObjectArrayList<Constraint> vincoli_negati, ObjectArrayList<Forbidden> lista_forbidden, Graph folded_g, ObjectIntOpenHashMap<String> folded_map, boolean onlyNotPath)
	{
		for (ObjectCursor<Node> n : folded_g.listaNodi()) {
			((Node)n.value).setMark(false);
		}
     
		String best_pred = attivita_iniziale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
     
		double best_pred_cs = 0.0D;
     
		double minZ = Double.MAX_VALUE;
     
		if (onlyNotPath) {
			minZ = 0.0D;
		}
		for (ObjectCursor<String> attivita_zCursor : lista_candidati_best_pred_unfolded) {
			String attivita_z = (String)attivita_zCursor.value;
       
			ObjectArrayList<Node> c_nodes = new ObjectArrayList<Node>();
       
			int violations_counter = 0;
       
			Forbidden f = new Forbidden(attivita_z.split("#")[0], ny.getNomeAttivita().split("#")[0]);
       
			if (!lista_forbidden.contains(f))
			{ 
				for (ObjectCursor<Constraint> cpn : vincoli_negati)
				{
					if (((Constraint)cpn.value).isPathConstraint())
					{
						if (((Constraint)cpn.value).getBodyList().contains(attivita_z.split("#")[0])) {
							for (String head : ((Constraint)cpn.value).getHeadList()) {
								c_nodes.add(new Node(head.split("#")[0], folded_map.get(head.split("#")[0])));
							}
						}
					}
				}

				Iterator localIterator5 = folded_g.listaNodi().iterator();
				Iterator localIterator6 = c_nodes.iterator();
				while(localIterator5.hasNext() && localIterator6.hasNext())
				{
					ObjectCursor<Node> c = (ObjectCursor)localIterator6.next();
				   
					for (ObjectCursor<Node> n : folded_g.listaNodi()) {
						((Node)n.value).setMark(false);
					}
					boolean path_violated = bfs(folded_g, folded_g.getNode(ny.getNomeAttivita().split("#")[0], folded_map.get(ny.getNomeAttivita().split("#")[0])), (Node)c.value, null, null);
					              
					if (path_violated) {
						violations_counter++;
					}
					Object n = (ObjectCursor)localIterator5.next();
					((Node)((ObjectCursor)n).value).setMark(false);
				}
         
				Node z = new Node(attivita_z.split("#")[0], folded_map.get(attivita_z.split("#")[0]));
         
				Iterator localIterator7 = folded_g.listaNodi().iterator();
				localIterator5 = folded_g.listaNodi().iterator();
				while(localIterator7.hasNext() && localIterator5.hasNext())
				{
					Object n = localIterator7.next();
					if (bfs(folded_g, (Node)((ObjectCursor)n).value, z, null, null))
					{
						for (Object cpn : vincoli_negati) {
							if (((Constraint)((ObjectCursor)cpn).value).isPathConstraint())
							{
								if ((((Constraint)((ObjectCursor)cpn).value).getBodyList().contains(((Node)((ObjectCursor<Node>)n).value).getNomeAttivita().split("#")[0])) && (((Constraint)((ObjectCursor)cpn).value).getHeadList().contains(ny.getNomeAttivita().split("#")[0])))
								{
									violations_counter++; } }
								}
							}
						Object nn = (ObjectCursor)localIterator5.next();
						((Node)((ObjectCursor)nn).value).setMark(false);
					}
				
				if (violations_counter < minZ) {
					minZ = violations_counter;
           
					best_pred = attivita_z;
					best_pred_cs = csm[map.get(attivita_z)][ny.getID_attivita()];
				}
				else if (violations_counter == minZ)
				{
					if (csm[map.get(attivita_z)][ny.getID_attivita()] > best_pred_cs) {
						best_pred = attivita_z;
						best_pred_cs = csm[map.get(attivita_z)][ny.getID_attivita()];
					}
				}
			}
		}     
		return best_pred;
	}
 
	private String getFinalBestSucc(Graph graph, double[][] csm, Node nx, ObjectIntOpenHashMap<String> map, ObjectArrayList<String> lista_candidati_best_succ_unfolded, ObjectArrayList<Constraint> vincoli_negati, ObjectArrayList<Forbidden> lista_forbidden, Graph folded_g, ObjectIntOpenHashMap<String> folded_map, boolean notPathOnly)
	{
		for (ObjectCursor<Node> n : folded_g.listaNodi()) {
			((Node)n.value).setMark(false);
		}
		Node x = folded_g.getNode(nx.getNomeAttivita().split("#")[0], folded_map.get(nx.getNomeAttivita().split("#")[0]));
     
		String best_succ = attivita_finale + "#" + String.format("%04d", new Object[] { Integer.valueOf(0) });
      
		double best_succ_cs = 0.0D;
		double minW = Double.MAX_VALUE;
     
		if (notPathOnly) {
			minW = 0.0D;
		}
		ObjectArrayList<Node> c_nodes = new ObjectArrayList();
      
		for (ObjectCursor<Constraint> cpn : vincoli_negati)
		{
			if (((Constraint)cpn.value).isPathConstraint())
			{
				if (((Constraint)cpn.value).getBodyList().contains(nx.getNomeAttivita().split("#")[0])) {
					for (String head : ((Constraint)cpn.value).getHeadList()) {
						c_nodes.add(new Node(head.split("#")[0], folded_map.get(head.split("#")[0])));
					}
				}
			}
		}
		for (ObjectCursor<String> attivita_w : lista_candidati_best_succ_unfolded)
		{
			int violations_counter = 0;
       
			Forbidden f = new Forbidden(nx.getNomeAttivita().split("#")[0], ((String)attivita_w.value).split("#")[0]);
        
			if (!lista_forbidden.contains(f))
			{
 
				Node nw = folded_g.getNode(((String)attivita_w.value).split("#")[0], folded_map.get(((String)attivita_w.value).split("#")[0]));
				Iterator localIterator5 = folded_g.listaNodi().iterator();
				Iterator localIterator4 = c_nodes.iterator();
				ObjectCursor<Node> n; 
					
				while(localIterator4.hasNext() && localIterator5.hasNext())
				{
					ObjectCursor<Node> c = (ObjectCursor)localIterator4.next();
					 
					boolean path_violated = bfs(folded_g, nw, (Node)c.value, null, null);
           
					if (path_violated) {
						violations_counter++;
					}
					n = (ObjectCursor)localIterator5.next();
					((Node)n.value).setMark(false);
				}
          
				localIterator4 = folded_g.listaNodi().iterator();
				Iterator localIterator6 = folded_g.listaNodi().iterator();
				
				while(localIterator4.hasNext() && localIterator6.hasNext())
				{
					n = (ObjectCursor)localIterator4.next();
					if (bfs(folded_g, (Node)n.value, x, null, null))
					{
						for (ObjectCursor<Constraint> cpn : vincoli_negati) {
							if ((((Constraint)cpn.value).isPathConstraint()) && 
								(((Constraint)cpn.value).getBodyList().contains(((Node)n.value).getNomeAttivita().split("#")[0])) && (((Constraint)cpn.value).getHeadList().contains(((String)attivita_w.value).split("#")[0])))
							{
								violations_counter++; 
							}
						}
					}
					ObjectCursor<Node> nn = (ObjectCursor<Node>) localIterator6.next();
					((Node)nn.value).setMark(false);
				}
				if (violations_counter < minW)
				{
					best_succ = (String)attivita_w.value;
					best_succ_cs = csm[nx.getID_attivita()][map.get((String)attivita_w.value)];
           
					minW = violations_counter;
				}
				else if ((violations_counter == minW) && 
					(csm[nx.getID_attivita()][map.get((String)attivita_w.value)] > best_succ_cs)) {
					best_succ = (String)attivita_w.value;
					best_succ_cs = csm[nx.getID_attivita()][map.get((String)attivita_w.value)];
				}
			}
		}
		return best_succ;
	}
 
	public Graph getGrafoAggregato(Graph g, XLog log, boolean flag, ObjectIntOpenHashMap<String> mapOri, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracceOri, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivitaOri)
	{
		if (flag)
		{
			time += System.currentTimeMillis();
        
			int count = 0;
			
			for (int i = 0; i < log.size(); i++) {
				XTrace trace = (XTrace)log.get(i);
				String traccia = trace.getAttributes().get("concept:name") + " # " + i;
         
				if (!traccia_attivitaOri.containsKey(traccia)) {
					traccia_attivitaOri.put(traccia, new ObjectArrayList());
				}
				for (XEvent activity : trace)
				{
 
					String nome_attivita = activity.getAttributes().get("concept:name").toString();
           
					if (!mapOri.containsKey(nome_attivita)) {
						mapOri.put(nome_attivita, count);
						count++;
					}           
 
					if (!attivita_tracceOri.containsKey(nome_attivita)) {
						ObjectArrayList<String> lista_tracce = new ObjectArrayList();
						lista_tracce.add(traccia);
						attivita_tracceOri.put(nome_attivita, lista_tracce);
					}
           
					((ObjectArrayList)attivita_tracceOri.get(nome_attivita)).add(traccia);
          
					((ObjectArrayList)traccia_attivitaOri.get(traccia)).add(nome_attivita);
				}
			}
		}
		if (!flag) {
			time += System.currentTimeMillis();
		}     
		ObjectArrayList<Edge> lista_archi_unfolded = g.getLista_archi();
     
		Graph graph = new Graph();
     
		Object[] keys = mapOri.keys;
		int[] values = mapOri.values;
     
		for (int i = 0; i < mapOri.allocated.length; i++) {
			if (mapOri.allocated[i] != false) {
				String key = (String)keys[i];
				Integer value = Integer.valueOf(values[i]);
				Node node = new Node(key, value.intValue());
	         
				if (!graph.getMap().containsKey(node))
					graph.getMap().put(node, new ObjectOpenHashSet());
			}
		}
		keys = g.getMap().keys;
     
		Object[] vals = g.getMap().values;
     
		for (int i = 0; i < g.getMap().allocated.length; i++) {
			if (g.getMap().allocated[i] != false) {
				Node n = (Node)keys[i];
         
				ObjectOpenHashSet<Node> n_adjacents = (ObjectOpenHashSet)vals[i];
         
				int it1 = 0;
	         
				while (it1 < graph.listaNodi().size())
				{
					Node newnode = (Node)graph.listaNodi().get(it1);
           
					if (newnode.getNomeAttivita().equals(n.getNomeAttivita().split("#")[0]))
					{
						for (ObjectCursor<Node> n_k : n_adjacents)
						{ 
							int it = 0;
               
							while (it < graph.listaNodi().size())
							{
								Node new_n_k = (Node)graph.listaNodi().get(it);
								if (new_n_k.getNomeAttivita().equals(((Node)n_k.value).getNomeAttivita().split("#")[0]))
								{
 
									if (((ObjectOpenHashSet)graph.getMap().get(newnode)).contains(new_n_k)) break;
									for (ObjectCursor<Edge> e : lista_archi_unfolded) {
										if (((Edge)e.value).equals(new Edge(n, (Node)n_k.value))) {
											graph.addEdge(newnode, new_n_k, ((Edge)e.value).isFlag());
                       
											newnode.incr_Outer_degree();
											new_n_k.incr_Inner_degree();
											break;
										}
									}
									break;
								}
								it++;
							}
						}
					}           
					it1++;
				}
			}
		}
     
		time += System.currentTimeMillis() - time;
     
		return graph;
	}
 
	public String getKeyByValue(ObjectIntOpenHashMap<String> map, int value)
	{
		Object[] keys = map.keys;
     
		for (int i = 0; i < map.allocated.length; i++) {
			if ((map.allocated[i] != false) && 
				(value == map.values[i])) {
				return (String)keys[i];
			}
		}
		System.out.println("Errore key non trovata per id " + value);
		return null;
	} 
 
	public Graph createGraphFromPNML(String fileName, InputStream input, ObjectIntOpenHashMap<String> folded_map)
		throws XmlPullParserException, IOException
	{
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(true);
		XmlPullParser xpp = factory.newPullParser();
     
		xpp.setInput(input, null);
		int eventType = xpp.getEventType();
		Pnml pnml = new Pnml();
		
		while (eventType != 2) {
			eventType = xpp.next();
		}
     
		if (xpp.getName().equals("pnml")) {
			pnml.importElement(xpp, pnml);
		} else {
			pnml.log("pnml", xpp.getLineNumber(), "Expected pnml");
		}
		if (pnml.hasErrors()) {
			return null;
		}
		
		Petrinet petrinet = PetrinetFactory.newPetrinet(fileName);
     
		pnml.convertToNet(petrinet, new Marking(), new GraphLayoutConnection(petrinet));
     
		Graph g = new Graph();
     
		Iterator<? extends Transition> it = petrinet.getTransitions().iterator();
     
		HashMap<PetrinetNode, Node> hashmap = new HashMap();
     
		while (it.hasNext())
		{
			Transition t = (Transition)it.next();
			String s = t.getLabel();
       
			if (!s.startsWith("[")) {
				if (s.contains("+")) {
					s = s.split("\\+")[0];
				}
				if (folded_map.containsKey(s)) {
					Node n = new Node(s, folded_map.get(s));
					g.getMap().put(n, new ObjectOpenHashSet());
           
					hashmap.put(t, n);
				}
				else {
					t.setInvisible(true);
				}
			} else {
				t.setInvisible(true);
			}
		}
		it = petrinet.getTransitions().iterator();
     
		while (it.hasNext()) {
			Transition t = (Transition)it.next();
			if (!t.isInvisible()) {
				Node n = (Node)hashmap.get(t);
				for (Transition successor : t.getVisibleSuccessors()) {
					if (!successor.isInvisible()) {
						Node adjacent = (Node)hashmap.get(successor);
						g.addEdge(n, adjacent, false);
					}
				}
			}
		}
		return g;
	}
    
	private ObjectOpenHashSet<String> getPredecessors_FoldedLocal(String trace, String activity_x, String activity_y, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita)
	{
		ObjectOpenHashSet<String> predecessors_traccia = new ObjectOpenHashSet();
      
		int i = 0;
		while (i < ((ObjectArrayList)traccia_attivita.get(trace)).size())
		{
			String activity_Z = (String)((ObjectArrayList)traccia_attivita.get(trace)).get(i);
       
			if (activity_Z.equals(activity_x))
				break;
			if (!activity_Z.split("#")[0].equals(activity_y.split("#")[0]))
			{
				if (!predecessors_traccia.contains(activity_Z.split("#")[0])) {
					predecessors_traccia.add(activity_Z.split("#")[0]);
				}
			}
			i++;
		}
     
		return predecessors_traccia;
	}   
 
	public ObjectArrayList<Edge> removableEdges(Graph g, double[][] cs, ObjectArrayList<Constraint> folded_vincoli_positivi, ObjectIntOpenHashMap<String> folded_map, double relative_to_best)
	{
		ObjectArrayList<Edge> removableEdges = new ObjectArrayList();
		ObjectArrayList<Edge> listaArchi = new ObjectArrayList(g.getLista_archi());
		
		for (ObjectCursor<Edge> ee : listaArchi) {
			Edge e = (Edge)ee.value;
        
			if ((verificaVincoliPositivi(g, e.getX(), e.getY(), folded_vincoli_positivi, folded_map)) && 
				(bestScore(g, e.getX(), e.getY(), cs) > relative_to_best))
			{
				ObjectIntOpenHashMap<IntOpenHashSet> obX = e.getX().getOutput();
         
				ObjectIntOpenHashMap<IntOpenHashSet> ibY = e.getY().getInput();
				Object[] keys = obX.keys;
         
				for (int i = 0; i < obX.allocated.length; i++) {
					if (obX.allocated[i] != false) {
						IntOpenHashSet ts = (IntOpenHashSet)keys[i];
						if ((ts.contains(e.getY().getID_attivita())) && (ts.size() == 1))
							break;
					}
				}
				keys = ibY.keys;
				for (int i = 0; i < ibY.allocated.length; i++) {
					if (ibY.allocated[i] != false) {
						IntOpenHashSet ts = (IntOpenHashSet)keys[i];
						if ((ts.contains(e.getX().getID_attivita())) && (ts.size() == 1)) {
							break;
						}
					}
				}
				removableEdges.add(e);
			}
		}
      
		return removableEdges;
	}
   
	public double bestScore(Graph g, Node x, Node y, double[][] csm)
	{
		double bestcsOutX = 2.2250738585072014E-308D;
     
		double bestcsInY = 2.2250738585072014E-308D;
     
		for (int i = 0; i < g.adjacentNodes(x).size(); i++) {
			Node adjacent = (Node)g.adjacentNodes(x).get(i);
			if (csm[x.getID_attivita()][adjacent.getID_attivita()] > bestcsOutX)
				bestcsOutX = csm[x.getID_attivita()][adjacent.getID_attivita()];
		}
		for (int i = 0; i < g.listaNodi().size(); i++)
		{
			Node adjacent = (Node)g.listaNodi().get(i);
        
			if ((g.isConnected(adjacent, y)) && (csm[adjacent.getID_attivita()][y.getID_attivita()] > bestcsInY)) {
				bestcsInY = csm[adjacent.getID_attivita()][y.getID_attivita()];
			}
		}
		double bestScore = bestcsOutX < bestcsInY ? bestcsOutX : bestcsInY;
     
		return 1.0D - csm[x.getID_attivita()][y.getID_attivita()] / bestScore;
	}
 
	private ObjectOpenHashSet<String> getSuccessors_FoldedLocal(String trace, String activity_x, String activity_y, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita)
	{
		ObjectOpenHashSet<String> successors_traccia = new ObjectOpenHashSet();
     
		int i = ((ObjectArrayList)traccia_attivita.get(trace)).size() - 1;
     
		while (i >= 0)
		{
			String activity_W = (String)((ObjectArrayList)traccia_attivita.get(trace)).get(i);
       
			if (activity_W.equals(activity_x))
				break;
			if (!activity_W.split("#")[0].equals(activity_y.split("#")[0]))
			{
				if (!successors_traccia.contains(activity_W.split("#")[0])) {
					successors_traccia.add(activity_W.split("#")[0]);
				}
			}
			i--;
		}
		
		return successors_traccia;
	}
   
	public void postProcessing_dip_indirette(Graph g, ObjectIntOpenHashMap<String> map, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> attivita_tracce, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, double[][] cs, double sigma_2, ObjectArrayList<Constraint> vincoli_positivi)
	{
		ObjectArrayList<Node> adjacents;
    
		int cursor = 0;
		Iterator localIterator1 = g.listaNodi().iterator();
		do
		{	
			ObjectCursor<Node> nn = (ObjectCursor)localIterator1.next();
			Node n = (Node)nn.value;
			adjacents = g.adjacentNodes(n);
			adjacents.trimToSize();
				       
			if(cursor >= adjacents.size())
				continue;
				
			Node adjacent_i = (Node)adjacents.get(cursor);
			if ((n.getOuter_degree() == 1) || (adjacent_i.getInner_degree() == 1)) {
				cursor++;
			}
			else {
				ObjectOpenHashSet<String> candidati = new ObjectOpenHashSet();
				         
				for (ObjectCursor<Node> mm : g.listaNodi()) {
					Node m = (Node)mm.value;
				           
					if ((!m.equals(n)) && (!m.equals(adjacent_i)))
					{
						for (ObjectCursor<Node> e : g.listaNodi()) {
							((Node)e.value).setMark(false);
						}
				             
						boolean condizione_1 = bfs(g, n, m, adjacent_i, null);
				            				 
						for (Object e : g.listaNodi()) {
							((Node)((ObjectCursor)e).value).setMark(false);
						}
						boolean condizione_2 = g.isConnected(m, adjacent_i);
				            
						if ((condizione_1) && (condizione_2))
						{
							candidati.add(m.getNomeAttivita());
						}
					}
				}				     
				if (candidati.size() > 0)
				{
					if (!verificaVincoliPositivi(g, n, adjacent_i, vincoli_positivi, map))
					{
						cursor++;
						continue;
					}
					ObjectArrayList<String> lista_tracce_n = new ObjectArrayList((ObjectContainer)attivita_tracce.get(n.getNomeAttivita()));
					lista_tracce_n.trimToSize();
				           
					Object lista_tracce_i = new ObjectOpenHashSet((ObjectContainer)attivita_tracce.get(adjacent_i.getNomeAttivita()));
				           				 
					lista_tracce_n.retainAll((ObjectLookupContainer)lista_tracce_i);
				        
					boolean rimuovi_arco = true;
				           
					if (lista_tracce_n.size() == 0)
					{
						rimuovi_arco = false;
					}
				           
					int counter = 0;
				           
					int it1 = 0;
					while ((it1 < lista_tracce_n.size()) && (rimuovi_arco))
					{
						String trace_1 = (String)lista_tracce_n.get(it1);
				        
						if (!esisteAttivatore(trace_1, n.getNomeAttivita(), adjacent_i.getNomeAttivita(), traccia_attivita, candidati, true, false, false)) {
							counter++;
							if (counter > sigma_2 * lista_tracce_n.size())
							{
								rimuovi_arco = false;
							}
						}
						it1++;
					}
				        
					if (rimuovi_arco) {
						g.removeEdge(n, adjacent_i);
				   
						n.decr_Outer_degree();
						adjacent_i.decr_Inner_degree();
					}
				}
				cursor++;
			}
					
		}
		while(localIterator1.hasNext());
	}
 
	public boolean verificaVincoliPositivi(Graph graph, Node np, Node nr, ObjectArrayList<Constraint> vincoli_positivi, ObjectIntOpenHashMap<String> map)
	{
		if ((np != null) && (nr != null)) {
			graph.removeEdge(np, nr);
		}
		for (ObjectCursor<Constraint> cc : vincoli_positivi)
		{
			Constraint c = (Constraint)cc.value;
       
			boolean path_constraint = c.isPathConstraint();
       
			boolean vincolo_soddisfatto = false;
			Iterator localIterator3 = c.getBodyList().iterator();
			Iterator localIterator2 = c.getHeadList().iterator();
			while(localIterator2.hasNext() && localIterator3.hasNext()){
				String head = (String)localIterator2.next();
					         
				Node nHead = graph.getNode(head, map.get(head));
         
				String body = (String)localIterator3.next();
         
				Node nBody = graph.getNode(body, map.get(body));
         
				if (graph.isConnected(nBody, nHead)) {
					vincolo_soddisfatto = true;
           
					break;
				}
				if (path_constraint)
				{
 
					for (int ni = 0; ni < graph.listaNodi().size(); ni++) {
						Node n = (Node)graph.listaNodi().get(ni);
						n.setMark(false);
					}
           
					if (bfs(graph, nBody, nHead, null, null)) {
						vincolo_soddisfatto = true;
             
						break;
					}
				}
			}
			if (!vincolo_soddisfatto)
			{
				if ((np != null) && (nr != null))
					graph.addEdge(np, nr, false);
				return false;
			}
		}
 
		if ((np != null) && (nr != null))
			graph.addEdge(np, nr, false);
		return true;
	} 
 
	public void computeBindings(Graph g, ObjectObjectOpenHashMap<String, ObjectArrayList<String>> traccia_attivita, ObjectIntOpenHashMap<String> map)
	{
		Object[] values = traccia_attivita.values;
		ObjectArrayList<String> traccia; 
		for (int it1 = 0; it1 < traccia_attivita.allocated.length; it1++) {
			if (traccia_attivita.allocated[it1] != false) {
				traccia = (ObjectArrayList)values[it1];
         
				IntOpenHashSet[] outputBindings = new IntOpenHashSet[traccia.size()];
				IntArrayList[] outputBindingsExtended = new IntArrayList[traccia.size()];
         
				IntOpenHashSet[] inputBindings = new IntOpenHashSet[traccia.size()];
				IntArrayList[] inputBindingsExtended = new IntArrayList[traccia.size()];
         
				for (int i = 0; i < traccia.size(); i++) {
					outputBindings[i] = new IntOpenHashSet();
					outputBindingsExtended[i] = new IntArrayList();
					inputBindings[i] = new IntOpenHashSet();
					inputBindingsExtended[i] = new IntArrayList();
				}
         
				int[] activitiesIDMapping = new int[traccia.size()];
         
				for (int i = 0; i < traccia.size(); i++)
				{
					String activity = (String)traccia.get(i);
					activitiesIDMapping[i] = map.get(activity);
           
					boolean verificato = false;
            
					for (int j = i + 1; j < traccia.size(); j++)
					{
						String successor = (String)traccia.get(j);
             
						if (g.isConnected(new Node(activity, map.get(activity)), new Node(successor, map.get(successor))))
						{
							if (!verificato)
							{
								if (!outputBindings[i].contains(map.get(successor)))
									outputBindings[i].add(map.get(successor));
								if (!inputBindings[j].contains(map.get(activity))) {
									inputBindings[j].add(map.get(activity));
								}
                 
								outputBindingsExtended[i].add(map.get(successor));
                 
								inputBindingsExtended[j].add(map.get(activity));
                 
								verificato = true; 
							}
							else
							{
								outputBindingsExtended[i].add(map.get(successor));
                 
								inputBindingsExtended[j].add(map.get(activity));
							}
						}
					}           
					verificato = false;
 
					for (int j = i - 1; j >= 0; j--)
					{
						String predecessor = (String)traccia.get(j);
             
						if (g.isConnected(new Node(predecessor, map.get(predecessor)), new Node(activity, map.get(activity))))
						{
							if (!verificato)
							{
								if (!outputBindings[j].contains(map.get(activity))) {
									outputBindings[j].add(map.get(activity));
								}
								if (!inputBindings[i].contains(map.get(predecessor))) {
									inputBindings[i].add(map.get(predecessor));
								}
                 
								inputBindingsExtended[i].add(map.get(predecessor));
                 
								outputBindingsExtended[j].add(map.get(activity));
								
								verificato = true;
 
							}
							else
							{
								inputBindingsExtended[i].add(map.get(predecessor));
                 
								outputBindingsExtended[j].add(map.get(activity));
							}
						}
					}
				}
         
				for (int k = 0; k < activitiesIDMapping.length; k++)
				{
					if (!g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getOutput().containsKey(outputBindings[k])) {
						g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getOutput().put(outputBindings[k], 1);
					}           
					if (!g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getInput().containsKey(inputBindings[k]))
					{
						g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getInput().put(inputBindings[k], 1);
					}
					if (!g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getExtendedOutput().containsKey(outputBindingsExtended[k]))
					{
						g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getExtendedOutput().put(outputBindingsExtended[k], 1);
					}           
					if (!g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getExtendedInput().containsKey(inputBindingsExtended[k]))
					{
						g.getNode(getKeyByValue(map, activitiesIDMapping[k]), activitiesIDMapping[k]).getExtendedInput().put(inputBindingsExtended[k], 1);
					}
				}
			}
		}
     
		for (ObjectCursor<Edge> ee : g.getLista_archi()) {
			Edge e = (Edge)ee.value;
			if (e.isFlag())
			{
	        	IntOpenHashSet treeSetOut = new IntOpenHashSet();
	       	 	treeSetOut.add(e.getY().getID_attivita());
	       	 	if (!e.getX().getOutput().containsKey(treeSetOut)) {
	       	 		e.getX().getOutput().put(treeSetOut, 1);
	       	 		IntOpenHashSet treeSetIn = new IntOpenHashSet();
	       	 		treeSetIn.add(e.getX().getID_attivita());
	       	 		e.getY().getInput().put(treeSetIn, 1);
	       	 	}
			}
		}
	}
}

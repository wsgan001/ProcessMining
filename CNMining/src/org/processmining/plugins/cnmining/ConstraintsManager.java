package org.processmining.plugins.cnmining;

import com.carrotsearch.hppc.ObjectArrayList;

/*
 * Questa classe funge da contenitore dei vincoli 
 */

public class ConstraintsManager {

    ObjectArrayList<Forbidden> 
    	forbidden, 
    	forbiddenUnfolded;
    ObjectArrayList<Constraint>	
    	positivi, 
    	negati, 
    	positiviUnfolded, 
    	negatiUnfolded;
    
    public ConstraintsManager(){
    	forbidden = new ObjectArrayList<Forbidden>();
    	forbiddenUnfolded = new ObjectArrayList<Forbidden>();
        positivi = new ObjectArrayList<Constraint>();	    
        negati = new ObjectArrayList<Constraint>();
        positiviUnfolded = new ObjectArrayList<Constraint>();	    
        negatiUnfolded = new ObjectArrayList<Constraint>();
    }
    
}

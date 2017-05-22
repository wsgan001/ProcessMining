package org.processmining.plugins.rttmining;

/*
 * Questa classe contiene le configurazioni generali del plugin
 */

public class Settings
{
	private String constr_file_name = "";
	private boolean constraintsEnabled;
	private double sigma_log_noise;
	private double fallFactor;
	private String logName;
	private double rtb;
  
	public boolean areConstraintsEnabled(){
		return this.constraintsEnabled;
	}
  
	public void setConstraintsEnabled(boolean constraintsEnabled){
		this.constraintsEnabled = constraintsEnabled;
	}
  
	public double getSigmaLogNoise(){
		return this.sigma_log_noise;
	}
  
  	public void setSigmaLogNoise(double sigmaLogNoise){
  		this.sigma_log_noise = sigmaLogNoise;
  	}
  
  	public String getConstraintsFilename(){
  		return this.constr_file_name;
  	}
  
  	public void setLogName(String log){
  		this.logName = log;
  	}
  
  	public String getLogName(){
  		return this.logName;
  	}
  
  	public void setFallFactor(double fallFactor){
  		this.fallFactor = fallFactor;
  	}
  
  	public void setRelativeToBest(double rtb){
  		this.rtb = rtb;
  	}
  
  	public double getFallFactor(){
  		return this.fallFactor;
  	}
  
  	public double getRelativeToBest(){	
  		return this.rtb;
  	}
  
  	public void setConstraintsFilename(String constr_file_name){
  		this.constr_file_name = constr_file_name;
  	}
}

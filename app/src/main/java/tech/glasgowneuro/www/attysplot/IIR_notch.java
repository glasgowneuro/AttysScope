package tech.glasgowneuro.www.attysplot;

import java.lang.Math;

public class IIR_notch {

	private float[] buffer= new float[3];
	private float norm;
	private float[] numerator = new float[3];
	private float[] denominator= new float[3];
    private boolean isActive = true;

	IIR_notch() {
		buffer[0]=0;
  		buffer[1]=0;
  		buffer[2]=0;
  		norm=1;
	}


    boolean getIsActive() {return isActive;}

    void setIsActive(boolean active) { isActive = active;}


	float filter(float value) {
  		float input=value;
  		float output=(numerator[1]*buffer[1]);
  		input=input-(denominator[1]*buffer[1]);
  		output=output+(numerator[2]*buffer[2]);
  		input=input-(denominator[2]*buffer[2]);
  		output=output+input* numerator[0];
  		buffer[2]=buffer[1];
  		buffer[1]=input;
  		output=output/norm;
        if (!isActive) {
            return value;
        }
  		return output;
	}


	void setParameters(float f,float r) {
  		numerator[0]=1;
  		numerator[1]=-2.0F*(float)Math.cos(2*Math.PI*f);
  		numerator[2]=1;
  		denominator[0]=1;
  		denominator[1]=-2.0F*r*(float)Math.cos(2*Math.PI*f);
  		denominator[2]=r*r;
	}

}


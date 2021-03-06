/*
This file is part of Free Analysis and Interactive Reconstruction
for Structured Illumination Microscopy (fairSIM).

fairSIM is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

fairSIM is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with fairSIM.  If not, see <http://www.gnu.org/licenses/>
*/

package org.fairsim.linalg;

import org.fairsim.utils.SimpleMT;

import java.util.Map;
import java.util.TreeMap;

/**
 * Provides FFTs for vector elements.
 */
public abstract class Transforms {

    /** Two-dimensional FFT of the input vector. */
    static public void fft2d( Vec2d.Cplx in, boolean inverse ) {
	// get parameters
	final int w = in.width;
	final int h = in.height;
	// see if we have an instance already, otherwise make one
	Transforms ffti = getOrCreateInstance(new FFTkey(w,h));
	ffti.fft_2d_trans_c2c( in.data , inverse );
    }

    /** One-dimensional FFT of the complex input vector. */
    static public void fft1d( Vec.Cplx in, boolean inverse ) {
	final int len = in.elemCount;
	Transforms ffti = getOrCreateInstance( new FFTkey(len));
	ffti.fft_1d_trans_c2c( in.data, inverse );
    }

    /** One-dimensional FFT of a standard float array. This assumes
     *  standard packing ( a[i*2] = real[i], a[i*2+1] =imag[i] ) of
     *  the input array */
    static public void fft1d( float [] in, boolean inverse ) {
	final int len = in.length/2;
	Transforms ffti = getOrCreateInstance( new FFTkey(len));
	ffti.fft_1d_trans_c2c( in, inverse );
    }

    // -----------------------------------------------------------
    // Instance management


    /** key to store instances */
    private static class FFTkey implements  Comparable<FFTkey> { 
	final int d,x,y ; 
	FFTkey( int xi ) {
	    d=1; x=xi; y=-1;
	}
	FFTkey( int xi, int yi ) { 
	    d=2; x=xi; y=yi;
	}
	@Override
	public int compareTo(FFTkey t) {
	    if (d != t.d) return (t.d -d );
	    if (x != t.x) return (t.x -x );
	    if (y != t.y) return (t.y -y );
	    return 0;
	}
    }
    
    /** FFT instances */
    static private Map<FFTkey, Transforms> instances; 
    
    /** static initialization of the instances list. */
    static {
	if (instances==null) {
	    instances = new TreeMap<FFTkey, Transforms>();
	}
    }



    /** returns an instance, creates one if none exists */
    static protected Transforms getOrCreateInstance(final FFTkey k) {
	Transforms ffti = instances.get(k);
	if (ffti!=null) return ffti;
	//Tool.trace("FFT: creating new instance");
	if (k.d==1)
	    ffti = new JTransformsConnector(k.x);
	if (k.d==2)
	    ffti = new JTransformsConnector(k.x,k.y);
	if (ffti==null) 
	    throw new RuntimeException("Unsupported dimensions");
	instances.put( k , ffti );
	return ffti;
    }


    // ---------------------------------------------------------
    //
    // abtract functions for the actual transformations
    //
    // ---------------------------------------------------------

    /** 
     *  1D Fourier tranformation complexFloat2complexFloat.
     *  This has to be implemented in a subclass. For convenience,
     *  all necessary parameters are passed.
     */
    protected abstract void fft_1d_trans_c2c(  float [] x, boolean inverse );
    /** 
     *  2D Fourier tranformation complexFloat2complexFloat.
     *  This has to be implemented in a subclass. For convenience,
     *  all necessary parameters are passed.
     */
    protected abstract void fft_2d_trans_c2c(  float [] x, boolean inverse );


    /** checks if input is 2^n */
    static public boolean powerOf2(int l) {
	    int i=2;
	    while(i<l) i *= 2;
	    return (i==l);
    }
    
    // ---------------------------------------------------------
    //
    // Power spectrum calculations
    //
    // ---------------------------------------------------------

    /** Computes a power spectrum, assuming the input vector is an FFT spectrum.
     *	Note: The spectrum will be clipped and quadrant-swapped for display purposes. */
    static public void computePowerSpectrum( Vec2d.Cplx inV, Vec2d.Real outV) {
	    computePowerSpectrum( inV, outV, true );
    }
    
    /** Computes a power spectrum, assuming the input vector is an FFT spectrum.
     *  Note: The spectrum is clipped to log(max)-log(min)<30, and can be quadrand-swapped. */
    static public void computePowerSpectrum( Vec2d.Cplx inV, Vec2d.Real outV, 
	boolean swap_quad ) {


	// TODO: maybe speed this up to higher efficience
	final int w= inV.width;
	final int h= inV.height;
	float [] in  =  inV.data;
	float [] out  = outV.data;

	if (( w != outV.width  ) || ( h != outV.height ))
	    throw new RuntimeException("Vector size mismatch");



	// calculate min and max for scaling
	float min = Float.MAX_VALUE;
	float max = Float.MIN_VALUE;
	for ( float i : in ) {
	    if (i<min) min=i;
	    if (i>max) max=i;
	}
	
	// reduce the range 
	max = (float)Math.log(max);
	min = (float)Math.log(min);
	if (Float.isNaN(min) || max-min>30)
	    min = max-30;

	
	for (int y=0;y<h;y++)
	for (int x=0;x<w;x++) {
	    float r = (float)Math.sqrt(
		Math.pow( in[2*(y*w+x)+0] , 2)+Math.pow( in[2*(y*w+x)+1] , 2));
	    r = (float)(((Math.log(r) - min)/(max-min)));
	    if (Float.isNaN(r) || r<0) r=0f;

	    if (swap_quad) {
		int xo = (x<w/2)?(x+w/2):(x-w/2);
		int yo = (y<h/2)?(y+h/2):(y-h/2);
		out[    yo*w    + xo ] = r;
	    } else {
		out[    y*w    + x ] = r;
	    }
	}
    
    }

    /** Swap quadrands. */
    static public void swapQuadrant(Vec2d.Cplx in) {
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	for (int y=0;y<h/2;y++)
	for (int x=0;x<w/2;x++) {
	    // 1 <-> 3
	    Cplx.Float tmp1 = in.get(x,y);
	    Cplx.Float tmp3 = in.get(x+w/2,y+h/2);
	    in.set(x,y,tmp3);
	    in.set(x+w/2,y+h/2,tmp1);
	    // 2 <-> 3
	    Cplx.Float tmp2 = in.get(x,y+h/2);
	    Cplx.Float tmp4 = in.get(x+w/2,y);
	    in.set(x,y+h/2,tmp4);
	    in.set(x+w/2,y,tmp2);
	}
    }
    
    /** Swap quadrands. */
    static public void swapQuadrant(Vec2d.Real in) {
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	for (int y=0;y<h/2;y++)
	for (int x=0;x<w/2;x++) {
	    // 1 <-> 3
	    float tmp1 = in.get(x,y);
	    float tmp3 = in.get(x+w/2,y+h/2);
	    in.set(x,y,tmp3);
	    in.set(x+w/2,y+h/2,tmp1);
	    // 2 <-> 3
	    float tmp2 = in.get(x,y+h/2);
	    float tmp4 = in.get(x+w/2,y);
	    in.set(x,y+h/2,tmp4);
	    in.set(x+w/2,y,tmp2);
	}
    }

    /** Return a vector containing phases for a Fourier shift theorems shift to kx,ky.
     *  @param N Width and heigt of vector
     *  @param kx x-coordinate of shift
     *  @param ky y-coordinate of shift
     *  @param fast Use faster, but less precise sin/cos (see {@link MTool#fsin}) */
    static public Vec2d.Cplx createShiftVector( 
	final int N, final double kx, final double ky, final boolean fast ) {
	Vec2d.Cplx shft = Vec2d.createCplx(N,N);
	final float [] val = shft.vectorData();
	
	// run outer loop in parallel
	new SimpleMT.PFor(0,N) {
	    public void at(int y) {
		for (int x=0; x<N; x++) {
		    float phaVal = (float)(2*Math.PI*(kx*x+ky*y)/N);
		    if (fast) {
			val[ (y*N+x)*2+0 ] = (float)MTool.fcos( phaVal );
			val[ (y*N+x)*2+1 ] = (float)MTool.fsin( phaVal );
		    } else {
			val[ (y*N+x)*2+0 ] = (float)Math.cos( phaVal );
			val[ (y*N+x)*2+1 ] = (float)Math.sin( phaVal );
		    }
		}
	    }
	};
	
	return shft;
    }

    /** See {@link #createShiftVector}, with fast='false' */
    static public Vec2d.Cplx createShiftVector(int N, double kx, double ky ) {
	return createShiftVector(N, kx, ky, false);
    }

    /** Multiply a vector with Fourier shift theorem phases.
     *  Vector has to be of square size (w==h).
     *  @param kx x-coordinate of shift
     *  @param ky y-coordinate of shift
     *  @param fast Use faster, but less precise sin/cos (see {@link MTool#fsin}) */
    static public void timesShiftVector( final Vec2d.Cplx vec,
	final double kx, final double ky, final boolean fast ) {
	final float [] val = vec.vectorData();
	final int N = Vec2d.checkSquare( vec );
	
	// run outer loop in parallel
	new SimpleMT.PFor(0,N) {
	    public void at(int y) {
		for (int x=0; x<N; x++) {
		    float phaVal = (float)(2*Math.PI*(kx*x+ky*y)/N);
		    float si,co;
		    if (fast) {
			co = (float)MTool.fcos( phaVal );
			si = (float)MTool.fsin( phaVal );
		    } else {
			co = (float)Math.cos( phaVal );
			si = (float)Math.sin( phaVal );
		    }
		    // get
		    float re = val[ (y*N+x)*2+0 ] ;
		    float im = val[ (y*N+x)*2+1 ] ;
		    // set
		    val[ (y*N+x)*2+0 ] = Vec.multReal( re, im, co, si );
		    val[ (y*N+x)*2+1 ] = Vec.multImag( re, im, co, si );
		}
	    }
	};
	
    }

    /** See {@link #timesShiftVector}, with fast='false' */
    static public void timesShiftVector( final Vec2d.Cplx vec, double kx,  double ky ) {
	timesShiftVector( vec, kx, ky, false );
    }



	
}

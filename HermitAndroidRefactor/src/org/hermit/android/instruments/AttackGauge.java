package org.hermit.android.instruments;

import org.hermit.android.core.SurfaceRunner;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.util.Log;


/**
 * Tryout for detecting and displaying attacks ( eg. playing of a note
 * with plectrum on a string instrument).
 * 
 * This class just overrides the methods of the similar and existing
 * WaveFormGauge.
 *   
 * @author wim
 */
public class AttackGauge extends WaveformGauge {

	/**
	 * Create a SpectrumGauge.
	 * 
	 * @param	parent		Parent surface.
     * @param   rate        The input sample rate, in samples/sec.
	 */
	AttackGauge(SurfaceRunner parent, int rate) {
	    super(parent);
	    
		beats = new BeatsQueue();
	    setSampleRate(rate);
	}
	
	
	/**
     * Set the sample rate for this instrument.
     * 
     * @param   rate        The desired rate, in samples/sec.
     */
    public void setSampleRate(int rate) {
    	if ( sample_rate != rate ) {
    		sample_rate = rate;
    		beats.clear();
    	}
    }
    
    
    class BeatsQueue {
    	private int[] beats = null;
    	private float interval = -1f;
    	private int index = -1;
    	private boolean filled = false;
    	private float best = 0f;
    	
    	private int cur_length = -1;
    	
    	public BeatsQueue() {
		}
    	
    	
    	public void add(int num_beats, int length) {
    		init(length);
    		
    		if ( num_beats <= 0 ) return;

    		++index;
    		if ( index >= beats.length ) {
    			filled = true;
    			index = 0;
    		}
    		beats[index] = num_beats;
    	}
    	
    	
    	private void init( int length) {
    		if ( beats == null || cur_length != length ) {
    			float sample_interval = 1f * length/sample_rate;
    			
    			int size  =  (int) ( (TIME_FRAME/1000)/sample_interval );
    			Log.d(TAG, "Setting beat size to " + size);
    			
    			beats = new int[size ];
    			interval = 1f * length / sample_rate;

    			cur_length = length;
    			clear();
    		}
    	}
 
    	
    	public float get_beats_per_second() {
    		int sum = 0;
    		int max = beats.length;
    		if ( !filled ) {
    			max = index + 1;
    		}
    		
    		if ( max == 0) return 0f;
    		
    		for ( int i = 0; i < max; ++ i) {
    			sum += beats[i];
    		}
    		
    		float ret = sum/( interval*max );
    		
    		if ( ret > best ) best = ret;
    		return ret;
    	}
 
    	
    	public float get_best_per_second() {
    		return best;
    	}

    	
    	public void clear() {
    		index = -1;
    		filled = false;
    		best = 0f;
    	}
    }
    
	/**
	 *  Buffer for storing local maxima in update().
	 * 
	 *  Implemented as float array, so that we don't have init objects. 
	 */
	class MaxVals {
		private static final int NUM_FIELDS = 3;
		private float[] max_buffer = null;
		public int max_index = 0;
		private float biggest_max;
		private float average;

		
		void init_buffer( int length ) {
			if ( max_buffer == null || max_buffer.length != length ) {
				max_buffer = new float[ NUM_FIELDS * length ];
			}
			
			max_index = 0;
			clear( max_index );
			
		}
		
		int length() { return max_index; }
		
		void get(float[] out, int index ) {
			for ( int i = 0; i < NUM_FIELDS; ++i) {
				out[i] = max_buffer[ NUM_FIELDS * index + i];
			}
		}

		void set(float[] in, int index ) {
			for ( int i = 0; i < NUM_FIELDS; ++i) {
				max_buffer[ NUM_FIELDS * index + i] = in[i];
			}
		}

		void copy( int from, int to ) {
			for ( int i = 0; i < NUM_FIELDS; ++i) {
				max_buffer[ NUM_FIELDS * to + i] = max_buffer[ NUM_FIELDS * from + i];
			}
		}

		
		
		/**
		 * Clear the values at given index location.
		 * 
		 * @param index
		 */
		void clear( int index ) {
			for ( int i = 0; i < NUM_FIELDS; ++i) {
				float val = 0f;
				if ( i == NUM_FIELDS - 1) val = -100f;
			
				max_buffer[ NUM_FIELDS * index + i] = val;
			}
		}

		void shift() {
			++max_index;
			clear( max_index );
		}
		
		
		/**
		 * Calculate line coefficient between two points in list.
		 * 
		 */
		private float coeff(int index1, int index2) {
            
            //if ( max_index < index1 || max_index < index2 ) return -100f;
            
            float[] val1 = new float[3];
            float[] val2 = new float[3];
            
            get( val1 , index1 );
            get( val2 , index2 );
            
			return ( val1[0] - val2[0])/(val1[1] - val2[1]);	
		}
		
		
        void set_max( float new_max, int i, boolean crossover) {
        	//TODO: get rid of these init's
            float[] next_to_last_max = new float[3];
            float[] last_max = new float[3];

            get( last_max , max_vals.max_index );

            if ( crossover && last_max[0] != 0) {
            	// Zero crossover detected

            	
            	// If larger than previous max, replace previous max
            	if ( max_index > 0 ) {
            		get( next_to_last_max , max_index - 1 );

            		last_max[2] = coeff( max_index, max_index - 1);

            		if ( next_to_last_max[0] < last_max[0] ) {
            			// OK, replace
                		set( last_max , max_index - 1 );
                		clear( max_index);
            		} else {
           				// Following to set coefficient
           				set( last_max , max_index );
           				shift();
            		}
            	} else {
            		shift();
            	}
            	
            } else {
            	if ( last_max[0] < new_max) {
            		last_max[0] = new_max;
            		last_max[1] = i;
            		
            		set( last_max , max_index );
            	}
            }
        }
        
        
        void calc_attributes() {
        	float total = 0f;
        	float max_max = 0f;
        	
			for ( int i = 0; i <= max_index; ++i) {
				float cur = max_buffer[ NUM_FIELDS * i];

				total += cur;
				if ( cur > max_max ) max_max = cur;
			}
			
			average = total/max_index;
			biggest_max = max_max;
        }

        float biggest_max() { return biggest_max; }
        float average() { return average; }

        float interpolate(float first_val, float coeff, float first_index, float test_index) {
        	return first_val + coeff*( test_index - first_index );
        }

        /* BROKEN
        float interpolate(int first_index, int last_index, int test_index) {
        	float[] first = new float[3];
        	//float[] last = new float[3];

        	get( first, first_index );
        	//get( last, last_index );
        	
        	float coeff = coeff(last_index, first_index);
        	
        	return interpolate( first[0], coeff, first_index, test_index);
        }
        */
        
        
        /**
         * 
         * @param from index of first item to delete
         * @param till index of one-past-last item to delete; non-inclusive
         */
        void remove_block(int from, int till) {
        	if ( from >= till) return;
        	
        	int copy_from = till;
        	int copy_to = from;
			for ( int i = 0; i <= (max_index - till); ++i) {
				copy( copy_from + i, copy_to + i );
			}
			
			max_index -= (till - from);
        }

        /**
         * ToDO: Doesn't work yet, debug.
         * 
         * @param threshold
         */
        public void threshold( float threshold) {
        	int right = max_index + 1;
        	
        	float[] val = new float[3];
        	
        	while ( right >= 0 ) {
        		int left;
        		
        		// Skip all too small values
        		for ( left = right -1; left >= 0; --left) {
        			get( val , left );
        			
        			if ( val[0] > threshold ) break;
        		}
        		
        		if ( left < right - 2) {
        			remove_block(left + 1, right);
        		}
        		
        		right = left;
        	}
        }
	}
	
	
	private MaxVals max_vals = new MaxVals();


	/**
     * Create a line envelope.
     * 
     * Going back to front, remove all points whose interpolated
     * values between other points is larger than actual value.
	 */
	private void make_envelope() {

        int num_skipped = 0;
        float[] first_val = new float[3];
        //float[] last_val  = new float[3];
        float[] test_val  = new float[3];
    	boolean found = false;
    	int last = max_vals.max_index;
    	
    	final int ENVELOPE_SIZE = 10;
 
        while ( last >= 2) {
        	//max_vals.get( last_val , last );
        	int first = last - ENVELOPE_SIZE;
        	if ( first < 0) first = 0;
        	
            for ( ; first <= last - 2; ++first) {
            	max_vals.get( first_val , first );

            	found = true;
            	
//                for ( int test = last - 1; test > first; --test) {
            	for ( int test = first + 1; test < last; ++test) {
                	max_vals.get( test_val , test );
                	
                	float coeff = max_vals.coeff( last, first);
                	
                    float interpolated = max_vals.interpolate( first_val[0], coeff, first_val[1], test_val[1]);
                    if ( interpolated < test_val[0] ) { 
                    	found = false;
                    	break;
                	}
                }
                
            	if ( found ) {
            		int to = first + 1;
            		num_skipped += last - to;	// Debug uutput
                
            		max_vals.remove_block(to, last );
            		last = to;	// NB: decremented later on
            		break;
            	}
            }
            
            --last;
        }
        
        if ( num_skipped > 0 ) {
        	Log.d(TAG, "Skipped " + num_skipped + " values.");
        }        
	}

	
	/**
	 * Determine scale factor for drawing current graph.
	 * 
	 * @param new_max max in current data
	 * @return scale factor to use
	 */
	private float set_scale_factor(float new_max) {
		// Calculate a scaling factor.
		// Max scale is determined by the biggest range we encountered this run.

		if ( new_max > max_range) {
			max_range = new_max;
		}

		float scale = (float) ( 0.9f * getDispHeight()/max_range );
		//float scale = 0.9f * getDispHeight()/ max_vals.biggest_max();

		if (scale < 0.001f || Float.isInfinite(scale))
			scale = 0.001f;
		else if (scale > 1000f)
			scale = 1000f;
		
		return scale;
	}

	
	@Override
    public void setGeometry(Rect bounds) {
	    super.setGeometry(bounds);

	    final float margin = getDispWidth() / 24;
	    graph_area = new Area(margin, 0, getDispWidth() - margin, getDispHeight() );
	    
	    text_area = new TextArea(graph_area.left(), graph_area.top(), 200, 100, "12.3 bps; best 12.3", getPaint() );
	}	
	

    public boolean needWaveForm() {
    	return true;
    }

	/**
	 * Override of parent, to add specific functionality.
	 * 
	 * TODO: properly handle off and len. For loops, init arrays.
	 */
	public void setWaveForm(short[] buffer, int off, int len, float bias, float range) {		
		max_vals.init_buffer( buffer.length );
		
        final Canvas canvas = getCanvas();
        final Paint paint = getPaint();
                
        final float uw = graph_area.width() / (float) len;
 
        // Now actually do the drawing.
        synchronized (this) {
            canvas.drawColor(0xff000000);

            // Draw the axes.
            paint.setColor(0xffffff00);
            paint.setStyle(Style.STROKE);
            canvas.drawLine( graph_area.left(), graph_area.top(), graph_area.left(), graph_area.bottom() - 1, paint);
            canvas.drawLine( graph_area.left(), graph_area.bottom() - 1, graph_area.right(), graph_area.bottom() - 1, paint);

            paint.setStyle(Style.STROKE);

            
            // Process and store max values
            for (int i = 1; i < len; ++i) {
            	float val1 = buffer[off + i -1] - bias;
                float val2 = buffer[off + i ] - bias;
                float abs2 = Math.abs(val2);
                
                max_vals.set_max(abs2, i, (val1*val2 < 0));
            }
 
            max_vals.calc_attributes();
            

            float scale = set_scale_factor( max_vals.biggest_max() );
            
            
            // Display found max values
            float[] disp_max = new float[3];
            float offset = 0;
            for ( int i = 0; i <= max_vals.max_index; ++i) {
            	max_vals.get( disp_max , i );
            	
            	float max2  = disp_max[0];
            	float index = disp_max[1];
            	
            	if ( i == 0 ) offset = index;
            	
            	final float x = graph_area.left() + (index - offset ) * uw;
                final float y0 = graph_area.bottom() - 1;
                final float y = y0 - max2 * scale;
                
                paint.setColor(0xff8888ff);
                canvas.drawLine( x, y0, x, y, paint);
            }
            
            
            // Get rid of values below a certain threshold
            //max_vals.threshold( max_vals.biggest_max()/4 );
            
            make_envelope();

            // Display the envelope
            offset = 0;
            float prev_x = 0;
            float prev_y = 0;
            for ( int i = 0; i <= max_vals.max_index; ++i) {
            	max_vals.get( disp_max , i );
            	
            	float max2  = disp_max[0];
            	float index = disp_max[1];
            	
            	//Note: offset comes from previous step!
            	//if ( i == 0 ) offset = disp_max[1];
            	
            	final float x = graph_area.left() + (index - offset ) * uw;
                final float y0 = graph_area.bottom() - 1;
                final float y = y0 - max2 * scale;

                // Draw the filtered out peaks with a different color
            	paint.setColor( 0xffff0000 );
                canvas.drawLine( x, y0, x, y, paint);
                
                // Draw enveloping line - skip last point if it's a zero
                if ( i != 0 && !( i == max_vals.max_index && max2 == 0  ) ) {
                	canvas.drawLine( prev_x, prev_y, x, y, paint);
                }

            	prev_x = x;
            	prev_y = y;
            }

            // -2 for taking off first and last values, which are usually zero.
            beats.add( max_vals.length() - 2, buffer.length );
            paint.setColor( 0xffcccccc );          
            String str = String.format("%.1f bps; best %.1f", beats.get_beats_per_second(), beats.get_best_per_second() );
        	text_area.draw( str, canvas, paint);
        }
    }
	
	// ******************************************************************** //
	// Class Data.
	// ******************************************************************** //
	
	/** Number of msec for which to determine hits per minute. */
	private static final int TIME_FRAME = 3000;
	
	// ******************************************************************** //
	// Private Data.
	// ******************************************************************** //
	
	private float max_range = 1f;
	private int sample_rate;
	private BeatsQueue beats;
	private Area graph_area;
	private TextArea text_area;
}

/*
 * Copyright (c) 2011 Apple Inc. All rights reserved.
 * Modified by Cymatic in 2026 for bounded integer decoding.
 *
 * @APPLE_APACHE_LICENSE_HEADER_START@
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * @APPLE_APACHE_LICENSE_HEADER_END@
 */

/*
	File:		ALACDecoder.cpp
*/

#include <stdlib.h>
#include <string.h>

#include "ALACDecoder.h"

#include "dplib.h"
#include "aglib.h"
#include "matrixlib.h"

#include "ALACBitUtilities.h"
#include "EndianPortable.h"

// constants/data
const uint32_t kMaxBitDepth = 32;			// max allowed bit depth is 32



/*
	Constructor
*/
ALACDecoder::ALACDecoder() :
	mMixBufferU( nil ),
	mMixBufferV( nil ),
	mPredictor( nil ),
	mShiftBuffer( nil )
{
	memset( &mConfig, 0, sizeof(mConfig) );
}

/*
	Destructor
*/
ALACDecoder::~ALACDecoder()
{
	// delete the matrix mixing buffers
	if ( mMixBufferU )
    {
		free(mMixBufferU);
        mMixBufferU = NULL;
    }
	if ( mMixBufferV )
    {
		free(mMixBufferV);
        mMixBufferV = NULL;
    }
	
	// delete the dynamic predictor's "corrector" buffer
	// - note: mShiftBuffer shares memory with this buffer
	if ( mPredictor )
    {
		free(mPredictor);
        mPredictor = NULL;
    }
}

/*
	Init()
	- initialize the decoder with the given configuration
*/
int32_t ALACDecoder::Init( void * inMagicCookie, uint32_t inMagicCookieSize )
{
	int32_t		status = ALAC_noErr;
    ALACSpecificConfig theConfig;
    uint8_t * theActualCookie = (uint8_t *)inMagicCookie;
    uint32_t theCookieBytesRemaining = inMagicCookieSize;

    RequireAction(inMagicCookie != nil && inMagicCookieSize == sizeof(ALACSpecificConfig),
                  return kALAC_ParamError; );

    // read the ALACSpecificConfig
    if (theCookieBytesRemaining >= sizeof(ALACSpecificConfig))
    {
        ALACSpecificConfig serialized;
        memcpy(&serialized, theActualCookie, sizeof(serialized));
        theConfig.frameLength = Swap32BtoN(serialized.frameLength);
        theConfig.compatibleVersion = serialized.compatibleVersion;
        theConfig.bitDepth = serialized.bitDepth;
        theConfig.pb = serialized.pb;
        theConfig.mb = serialized.mb;
        theConfig.kb = serialized.kb;
        theConfig.numChannels = serialized.numChannels;
        theConfig.maxRun = Swap16BtoN(serialized.maxRun);
        theConfig.maxFrameBytes = Swap32BtoN(serialized.maxFrameBytes);
        theConfig.avgBitRate = Swap32BtoN(serialized.avgBitRate);
        theConfig.sampleRate = Swap32BtoN(serialized.sampleRate);

        RequireAction(theConfig.frameLength >= 1 && theConfig.frameLength <= 16384 &&
                      theConfig.numChannels >= 1 && theConfig.numChannels <= 2 &&
                      (theConfig.bitDepth == 16 || theConfig.bitDepth == 20 ||
                       theConfig.bitDepth == 24 || theConfig.bitDepth == 32) &&
                      theConfig.sampleRate >= 8000 && theConfig.sampleRate <= 384000 &&
                      theConfig.kb >= 1 && theConfig.kb <= 14 &&
                      theConfig.maxFrameBytes <= 1024 * 1024,
                      return kALAC_ParamError; );
        mConfig = theConfig;
        
        RequireAction( mConfig.compatibleVersion <= kALACVersion, return kALAC_ParamError; );

        // allocate mix buffers
        mMixBufferU = (int32_t *) calloc( mConfig.frameLength * sizeof(int32_t), 1 );
        mMixBufferV = (int32_t *) calloc( mConfig.frameLength * sizeof(int32_t), 1 );

        // allocate dynamic predictor buffer
        mPredictor = (int32_t *) calloc( mConfig.frameLength * sizeof(int32_t), 1 );

        // "shift off" buffer shares memory with predictor buffer
        mShiftBuffer = (uint16_t *) mPredictor;
        
        RequireAction( (mMixBufferU != nil) && (mMixBufferV != nil) && (mPredictor != nil),
                        status = kALAC_MemFullError; goto Exit; );
     }
    else
    {
        status = kALAC_ParamError;
    }

    // skip to Channel Layout Info
    // theActualCookie += sizeof(ALACSpecificConfig);
    
    // Currently, the Channel Layout Info portion of the magic cookie (as defined in the 
    // ALACMagicCookieDescription.txt document) is unused by the decoder. 
    
Exit:
	return status;
}

/*
	Decode()
	- the decoded samples are interleaved into the output buffer in the order they arrive in
	  the bitstream
*/
int32_t ALACDecoder::Decode( BitBuffer * bits, uint8_t * sampleBuffer, uint32_t numSamples, uint32_t numChannels, uint32_t * outNumSamples )
{
	BitBuffer			shiftBits;
	uint32_t            bits1, bits2;
	uint8_t				tag;
	uint8_t				elementInstanceTag;
	AGParamRec			agParams;
	uint32_t				channelIndex;
	int16_t				coefsU[32];		// max possible size is 32 although NUMCOEPAIRS is the current limit
	int16_t				coefsV[32];
	uint8_t				numU, numV;
	uint8_t				mixBits;
	int8_t				mixRes;
	uint16_t			unusedHeader;
	uint8_t				escapeFlag;
	uint32_t			chanBits;
	uint8_t				bytesShifted;
	uint32_t			shift;
	uint8_t				modeU, modeV;
	uint32_t			denShiftU, denShiftV;
	uint16_t			pbFactorU, pbFactorV;
	uint16_t			pb;
	int16_t *			samples;
	int16_t *			out16;
	uint8_t *			out20;
	uint8_t *			out24;
	int32_t *			out32;
	uint8_t				headerByte;
	uint8_t				partialFrame;
	uint32_t			extraBits;
	int32_t				val;
	uint32_t			i, j;
	int32_t             status;
	
	RequireAction( (bits != nil) && (sampleBuffer != nil) && (outNumSamples != nil), return kALAC_ParamError; );
	RequireAction(numChannels == mConfig.numChannels && numSamples == mConfig.frameLength &&
                  !bits->failed, return kALAC_ParamError; );

	mActiveElements = 0;
	channelIndex	= 0;
	
	samples = (int16_t *) sampleBuffer;

	status = ALAC_noErr;
	*outNumSamples = numSamples;

	while ( status == ALAC_noErr )
	{
		// bail if we ran off the end of the buffer
    	RequireAction(!bits->failed && bits->cur < bits->end, status = kALAC_ParamError; goto Exit; );

		// copy global decode params for this element
		pb = mConfig.pb;

		// read element tag
		tag = BitBufferReadSmall( bits, 3 );
		switch ( tag )
		{
			case ID_SCE:
			{
				// mono/LFE channel
                RequireAction(channelIndex < numChannels, status = kALAC_ParamError; goto Exit; );
				elementInstanceTag = BitBufferReadSmall( bits, 4 );
				mActiveElements |= (1u << elementInstanceTag);

				// read the 12 unused header bits
				unusedHeader = (uint16_t) BitBufferRead( bits, 12 );
				RequireAction( unusedHeader == 0, status = kALAC_ParamError; goto Exit; );

				// read the 1-bit "partial frame" flag, 2-bit "shift-off" flag & 1-bit "escape" flag
				headerByte = (uint8_t) BitBufferRead( bits, 4 );
				
				partialFrame = headerByte >> 3;
				
				bytesShifted = (headerByte >> 1) & 0x3u;
				RequireAction(bytesShifted != 3 &&
                              (bytesShifted == 0 || mConfig.bitDepth >= 24),
                              status = kALAC_ParamError; goto Exit; );

				shift = bytesShifted * 8;

				escapeFlag = headerByte & 0x1;

				chanBits = mConfig.bitDepth - (bytesShifted * 8);
				
				// check for partial frame to override requested numSamples
				if ( partialFrame != 0 )
				{
					numSamples  = BitBufferRead( bits, 16 ) << 16;
					numSamples |= BitBufferRead( bits, 16 );
				}


                RequireAction(numSamples >= 1 && numSamples <= mConfig.frameLength &&
                              (channelIndex == 0 || numSamples == *outNumSamples) &&
                              (escapeFlag == 0 ? (chanBits >= 1 && chanBits <= 32) : bytesShifted == 0) &&
                              !bits->failed, status = kALAC_ParamError; goto Exit; );

				if ( escapeFlag == 0 )
				{
					// compressed frame, read rest of parameters
					mixBits	= (uint8_t) BitBufferRead( bits, 8 );
					mixRes	= (int8_t) BitBufferRead( bits, 8 );
                    RequireAction(mixBits == 0 && mixRes == 0, status = kALAC_ParamError; goto Exit; );
					//Assert( (mixBits == 0) && (mixRes == 0) );		// no mixing for mono

					headerByte	= (uint8_t) BitBufferRead( bits, 8 );
					modeU		= headerByte >> 4;
					denShiftU	= headerByte & 0xfu;
					
					headerByte	= (uint8_t) BitBufferRead( bits, 8 );
					pbFactorU	= headerByte >> 5;
					numU		= headerByte & 0x1fu;
                    RequireAction((modeU == 0 || modeU == 15),
                                  status = kALAC_ParamError; goto Exit; );

					for ( i = 0; i < numU; i++ )
						coefsU[i] = (int16_t) BitBufferRead( bits, 16 );
					
					// if shift active, skip the the shift buffer but remember where it starts
					if ( bytesShifted != 0 )
					{
						shiftBits = *bits;
						BitBufferAdvance( bits, (bytesShifted * 8) * numSamples ); 
					}

					// decompress
					set_ag_params( &agParams, mConfig.mb, (pb * pbFactorU) / 4, mConfig.kb, numSamples, numSamples, mConfig.maxRun );
					status = dyn_decomp( &agParams, bits, mPredictor, numSamples, chanBits, &bits1 );
					RequireNoErr( status, goto Exit; );

					if ( modeU == 0 )
					{
						unpc_block( mPredictor, mMixBufferU, numSamples, &coefsU[0], numU, chanBits, denShiftU );
					}
					else
					{
						// the special "numActive == 31" mode can be done in-place
						unpc_block( mPredictor, mPredictor, numSamples, nil, 31, chanBits, 0 );
						unpc_block( mPredictor, mMixBufferU, numSamples, &coefsU[0], numU, chanBits, denShiftU );
					}
				}
				else
				{
					//Assert( bytesShifted == 0 );

					// uncompressed frame, copy data into the mix buffer to use common output code
					shift = 32 - chanBits;
					if ( chanBits <= 16 )
					{
						for ( i = 0; i < numSamples; i++ )
						{
							val = (int32_t) BitBufferRead( bits, (uint8_t) chanBits );
							val = ((int32_t)((uint32_t)val << shift)) >> shift;
							mMixBufferU[i] = val;
						}
					}
					else
					{
						// BitBufferRead() can't read more than 16 bits at a time so break up the reads
						extraBits = chanBits - 16;
						for ( i = 0; i < numSamples; i++ )
						{
							val = (int32_t) BitBufferRead( bits, 16 );
							val = ((int32_t)((uint32_t)val << 16)) >> shift;
							mMixBufferU[i] = val | BitBufferRead( bits, (uint8_t) extraBits );
						}
					}

					mixBits = mixRes = 0;
					bits1 = chanBits * numSamples;
					bytesShifted = 0;
				}

				// now read the shifted values into the shift buffer
				if ( bytesShifted != 0 )
				{
					shift = bytesShifted * 8;
					//Assert( shift <= 16 );

					for ( i = 0; i < numSamples; i++ )
						mShiftBuffer[i] = (uint16_t) BitBufferRead( &shiftBits, (uint8_t) shift );
				}

				RequireAction(!bits->failed && (bytesShifted == 0 || !shiftBits.failed),
                              status = kALAC_ParamError; goto Exit; );

                // convert 32-bit integers into output buffer
				switch ( mConfig.bitDepth )
				{
					case 16:
						out16 = &((int16_t *)sampleBuffer)[channelIndex];
						for ( i = 0, j = 0; i < numSamples; i++, j += numChannels )
							out16[j] = (int16_t) mMixBufferU[i];
						break;
					case 20:
						out20 = (uint8_t *)sampleBuffer + (channelIndex * 3);
						copyPredictorTo20( mMixBufferU, out20, numChannels, numSamples );
						break;
					case 24:
						out24 = (uint8_t *)sampleBuffer + (channelIndex * 3);
						if ( bytesShifted != 0 )
							copyPredictorTo24Shift( mMixBufferU, mShiftBuffer, out24, numChannels, numSamples, bytesShifted );
						else
							copyPredictorTo24( mMixBufferU, out24, numChannels, numSamples );							
						break;
					case 32:
						out32 = &((int32_t *)sampleBuffer)[channelIndex];
						if ( bytesShifted != 0 )
							copyPredictorTo32Shift( mMixBufferU, mShiftBuffer, out32, numChannels, numSamples, bytesShifted );
						else
							copyPredictorTo32( mMixBufferU, out32, numChannels, numSamples);
						break;
				}

				channelIndex += 1;
				*outNumSamples = numSamples;
				break;
			}

			case ID_CPE:
			{
				// if decoding this pair would take us over the max channels limit, bail
				RequireAction(channelIndex + 2 <= numChannels, status = kALAC_ParamError; goto Exit; );

				// stereo channel pair
				elementInstanceTag = BitBufferReadSmall( bits, 4 );
				mActiveElements |= (1u << elementInstanceTag);

				// read the 12 unused header bits
				unusedHeader = (uint16_t) BitBufferRead( bits, 12 );
				RequireAction( unusedHeader == 0, status = kALAC_ParamError; goto Exit; );

				// read the 1-bit "partial frame" flag, 2-bit "shift-off" flag & 1-bit "escape" flag
				headerByte = (uint8_t) BitBufferRead( bits, 4 );
				
				partialFrame = headerByte >> 3;
				
				bytesShifted = (headerByte >> 1) & 0x3u;
				RequireAction(bytesShifted != 3 &&
                              (bytesShifted == 0 || mConfig.bitDepth >= 24),
                              status = kALAC_ParamError; goto Exit; );

				shift = bytesShifted * 8;

				escapeFlag = headerByte & 0x1;

				chanBits = mConfig.bitDepth - (bytesShifted * 8) + 1;
				
				// check for partial frame length to override requested numSamples
				if ( partialFrame != 0 )
				{
					numSamples  = BitBufferRead( bits, 16 ) << 16;
					numSamples |= BitBufferRead( bits, 16 );
				}


                RequireAction(numSamples >= 1 && numSamples <= mConfig.frameLength &&
                              (channelIndex == 0 || numSamples == *outNumSamples) &&
                              (escapeFlag == 0 ? (chanBits >= 1 && chanBits <= 32) : bytesShifted == 0) &&
                              !bits->failed, status = kALAC_ParamError; goto Exit; );

				if ( escapeFlag == 0 )
				{
					// compressed frame, read rest of parameters
					mixBits		= (uint8_t) BitBufferRead( bits, 8 );
					mixRes		= (int8_t) BitBufferRead( bits, 8 );
                    RequireAction(mixBits <= 31, status = kALAC_ParamError; goto Exit; );

					headerByte	= (uint8_t) BitBufferRead( bits, 8 );
					modeU		= headerByte >> 4;
					denShiftU	= headerByte & 0xfu;
					
					headerByte	= (uint8_t) BitBufferRead( bits, 8 );
					pbFactorU	= headerByte >> 5;
					numU		= headerByte & 0x1fu;
                    RequireAction((modeU == 0 || modeU == 15),
                                  status = kALAC_ParamError; goto Exit; );
					for ( i = 0; i < numU; i++ )
						coefsU[i] = (int16_t) BitBufferRead( bits, 16 );

					headerByte	= (uint8_t) BitBufferRead( bits, 8 );
					modeV		= headerByte >> 4;
					denShiftV	= headerByte & 0xfu;
					
					headerByte	= (uint8_t) BitBufferRead( bits, 8 );
					pbFactorV	= headerByte >> 5;
					numV		= headerByte & 0x1fu;
                    RequireAction((modeV == 0 || modeV == 15),
                                  status = kALAC_ParamError; goto Exit; );
					for ( i = 0; i < numV; i++ )
						coefsV[i] = (int16_t) BitBufferRead( bits, 16 );

					// if shift active, skip the interleaved shifted values but remember where they start
					if ( bytesShifted != 0 )
					{
						shiftBits = *bits;
						BitBufferAdvance( bits, (bytesShifted * 8) * 2 * numSamples );
					}

					// decompress and run predictor for "left" channel
					set_ag_params( &agParams, mConfig.mb, (pb * pbFactorU) / 4, mConfig.kb, numSamples, numSamples, mConfig.maxRun );
					status = dyn_decomp( &agParams, bits, mPredictor, numSamples, chanBits, &bits1 );
					RequireNoErr( status, goto Exit; );

					if ( modeU == 0 )
					{
						unpc_block( mPredictor, mMixBufferU, numSamples, &coefsU[0], numU, chanBits, denShiftU );
					}
					else
					{
						// the special "numActive == 31" mode can be done in-place
						unpc_block( mPredictor, mPredictor, numSamples, nil, 31, chanBits, 0 );
						unpc_block( mPredictor, mMixBufferU, numSamples, &coefsU[0], numU, chanBits, denShiftU );
					}

					// decompress and run predictor for "right" channel
					set_ag_params( &agParams, mConfig.mb, (pb * pbFactorV) / 4, mConfig.kb, numSamples, numSamples, mConfig.maxRun );
					status = dyn_decomp( &agParams, bits, mPredictor, numSamples, chanBits, &bits2 );
					RequireNoErr( status, goto Exit; );

					if ( modeV == 0 )
					{
						unpc_block( mPredictor, mMixBufferV, numSamples, &coefsV[0], numV, chanBits, denShiftV );
					}
					else
					{
						// the special "numActive == 31" mode can be done in-place
						unpc_block( mPredictor, mPredictor, numSamples, nil, 31, chanBits, 0 );
						unpc_block( mPredictor, mMixBufferV, numSamples, &coefsV[0], numV, chanBits, denShiftV );
					}
				}
				else
				{
					//Assert( bytesShifted == 0 );

					// uncompressed frame, copy data into the mix buffers to use common output code
					chanBits = mConfig.bitDepth;
					shift = 32 - chanBits;
					if ( chanBits <= 16 )
					{
						for ( i = 0; i < numSamples; i++ )
						{
							val = (int32_t) BitBufferRead( bits, (uint8_t) chanBits );
							val = ((int32_t)((uint32_t)val << shift)) >> shift;
							mMixBufferU[i] = val;

							val = (int32_t) BitBufferRead( bits, (uint8_t) chanBits );
							val = ((int32_t)((uint32_t)val << shift)) >> shift;
							mMixBufferV[i] = val;
						}
					}
					else
					{
						// BitBufferRead() can't read more than 16 bits at a time so break up the reads
						extraBits = chanBits - 16;
						for ( i = 0; i < numSamples; i++ )
						{
							val = (int32_t) BitBufferRead( bits, 16 );
							val = ((int32_t)((uint32_t)val << 16)) >> shift;
							mMixBufferU[i] = val | BitBufferRead( bits, (uint8_t)extraBits );

							val = (int32_t) BitBufferRead( bits, 16 );
							val = ((int32_t)((uint32_t)val << 16)) >> shift;
							mMixBufferV[i] = val | BitBufferRead( bits, (uint8_t)extraBits );
						}
					}

					bits1 = chanBits * numSamples;
					bits2 = chanBits * numSamples;
					mixBits = mixRes = 0;
					bytesShifted = 0;
				}

				// now read the shifted values into the shift buffer
				if ( bytesShifted != 0 )
				{
					shift = bytesShifted * 8;
					//Assert( shift <= 16 );

					for ( i = 0; i < (numSamples * 2); i += 2 )
					{
						mShiftBuffer[i + 0] = (uint16_t) BitBufferRead( &shiftBits, (uint8_t) shift );
						mShiftBuffer[i + 1] = (uint16_t) BitBufferRead( &shiftBits, (uint8_t) shift );
					}
				}

				RequireAction(!bits->failed && (bytesShifted == 0 || !shiftBits.failed),
                              status = kALAC_ParamError; goto Exit; );

                // un-mix the data and convert to output format
				// - note that mixRes = 0 means just interleave so we use that path for uncompressed frames
				switch ( mConfig.bitDepth )
				{
					case 16:
						out16 = &((int16_t *)sampleBuffer)[channelIndex];
						unmix16( mMixBufferU, mMixBufferV, out16, numChannels, numSamples, mixBits, mixRes );
						break;
					case 20:
						out20 = (uint8_t *)sampleBuffer + (channelIndex * 3);
						unmix20( mMixBufferU, mMixBufferV, out20, numChannels, numSamples, mixBits, mixRes );
						break;
					case 24:
						out24 = (uint8_t *)sampleBuffer + (channelIndex * 3);
						unmix24( mMixBufferU, mMixBufferV, out24, numChannels, numSamples,
									mixBits, mixRes, mShiftBuffer, bytesShifted );
						break;
					case 32:
						out32 = &((int32_t *)sampleBuffer)[channelIndex];
						unmix32( mMixBufferU, mMixBufferV, out32, numChannels, numSamples,
									mixBits, mixRes, mShiftBuffer, bytesShifted );
						break;
				}

				channelIndex += 2;
				*outNumSamples = numSamples;
				break;
			}

			case ID_CCE:
			case ID_LFE:
			case ID_PCE:
			{
				// unsupported element, bail
				//AssertNoErr( tag );
				status = kALAC_ParamError;
				break;
			}

			case ID_DSE:
			{
				// data stream element -- parse but ignore
				status = this->DataStreamElement( bits );
				break;
			}
			
			case ID_FIL:
			{
				// fill element -- parse but ignore
				status = this->FillElement( bits );
				break;
			}

			case ID_END:
			{
				// frame end, all done so byte align the frame and check for overruns
				RequireAction(channelIndex == numChannels && !bits->failed,
                              status = kALAC_ParamError; goto Exit; );
                if (bits->bitIndex != 0) {
                    uint8_t padding = 8 - bits->bitIndex;
                    RequireAction(BitBufferRead(bits, padding) == 0,
                                  status = kALAC_ParamError; goto Exit; );
                }
                RequireAction(!bits->failed && bits->cur == bits->end,
                              status = kALAC_ParamError; goto Exit; );
				goto Exit;
			}
		}

	}

    status = kALAC_ParamError;

Exit:
	return status;
}

#if PRAGMA_MARK
#pragma mark -
#endif

/*
	FillElement()
	- they're just filler so we don't need 'em
*/
int32_t ALACDecoder::FillElement( BitBuffer * bits )
{
	int16_t		count;
	
	// 4-bit count or (4-bit + 8-bit count) if 4-bit count == 15
	// - plus this weird -1 thing I still don't fully understand
	count = BitBufferReadSmall( bits, 4 );
	if ( count == 15 )
		count += (int16_t) BitBufferReadSmall( bits, 8 ) - 1;

	BitBufferAdvance( bits, count * 8 );

	RequireAction(!bits->failed, return kALAC_ParamError; );

	return ALAC_noErr;	
}

/*
	DataStreamElement()
	- we don't care about data stream elements so just skip them
*/
int32_t ALACDecoder::DataStreamElement( BitBuffer * bits )
{
	uint8_t		element_instance_tag;
	int32_t		data_byte_align_flag;
	uint16_t		count;
	
	// the tag associates this data stream element with a given audio element
	element_instance_tag = BitBufferReadSmall( bits, 4 );
	
	data_byte_align_flag = BitBufferReadOne( bits );

	// 8-bit count or (8-bit + 8-bit count) if 8-bit count == 255
	count = BitBufferReadSmall( bits, 8 );
	if ( count == 255 )
		count += BitBufferReadSmall( bits, 8 );

	// the align flag means the bitstream should be byte-aligned before reading the following data bytes
	if ( data_byte_align_flag )
		BitBufferByteAlign( bits, false );

	// skip the data bytes
	BitBufferAdvance( bits, count * 8 );

	RequireAction(!bits->failed, return kALAC_ParamError; );

	return ALAC_noErr;
}

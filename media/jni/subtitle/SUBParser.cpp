#define LOG_TAG "SUBParser"
#include "SUBParser.h"

namespace android
{

SUBParser::SUBParser()
{
     m_rSpData.m_pvSubtitlePacketBuffer = NULL;
}

SUBParser::SUBParser(const char * uri):mInitCheck(false),m_SpParser(NULL)
{
    if (NULL == uri)
    {
        ALOGE("uri is null");
        return;
    }
    mSUBFile= fopen(uri, "r");
    if (NULL == mSUBFile)
    {
        ALOGE("can't open uri: %s", uri);
        return;
    }
    m_rSpData.m_pvSubtitlePacketBuffer = NULL;
    m_SpParser = new VOBSubtitleParser();
    m_SpParser->stPrepareBitmapBuffer();
    mInitCheck = true;
}

SUBParser::~SUBParser()
{
    ALOGD("mSUBFile CLosed!");
    if (NULL != mSUBFile)
    {
        fclose(mSUBFile);
    }

    if (NULL != m_SpParser)
    {
       m_SpParser->stUnmapBitmapBuffer();
       delete m_SpParser;
       m_SpParser = NULL;
   }
}

status_t SUBParser::parse(int offset)
{
    ALOGD("[RY] parse from offset %d", offset);
    if (!mInitCheck)
    {
        return UNKNOWN_ERROR;
    }

    //1. generate VOB Subtitle Data Packet
    SUB_PARSE_STATE_E eState;
    int i = 0;
    status_t err = OK;

    do
    {
        eState = mParse(offset);
        i++;
    }
    while (eState == SUB_PARSE_NEXTLOOP);

    if (SUB_PARSE_DONE != eState)
    {
        return 1;
    }

    ALOGD("Parsing Done! Length is %d", m_rSpData.m_iLength);

    //2. parsing VOB Subtitle Data Packet
    m_SpParser->stInit(m_rSpData.m_pvSubtitlePacketBuffer, m_rSpData.m_iLength);

    do
    {
        m_SpParser->m_fgParseFlag = false;
        err = m_SpParser->stParseControlPacket();

        if (err != OK)
            break;

        if (m_SpParser->m_iDataPacketSize <= 4)
            break;

        if (err != OK)
            break;


        err = m_SpParser->stParseDataPacket();
    }
    while (false);

    m_SpParser->m_fgParseFlag = true;

    if (OK != err)
    {
        m_SpParser->vUnInit();
    }

    return err;
}

int SUBParser::iGetFileIdx()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->getTmpFileIdx();
    }
    else
    {
        return -1;
    }
}

void SUBParser::incFileIdx()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->incTmpFileIdx();
    }
}

int SUBParser::iGetStartTime()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iBeginTime;
    }
    else
    {
        return -1;
    }
}
int SUBParser::iGetSubtitleWidth()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iBitmapWidth;
    }
    else
    {
        return -1;
    }
}
int SUBParser::iGetSubtitleHeight()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iBitmapHeight;
    }
    else
    {
        return -1;
    }
}


int SUBParser::iGetBeginTime()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iBeginTime;
    }
    else
    {
        return -1;
    }
}

int SUBParser::iGetEndTime()
{
    if (NULL != m_SpParser)
    {
        return m_SpParser->m_iEndTime;
    }
    else
    {
        return -1;
    }
}



void SUBParser::vSetVOBPalette(const VOB_SUB_PALETTE palette)
{
    if (NULL != m_SpParser)
        m_SpParser->vSetPalette(palette);
}



SUBParser::SUB_PARSE_STATE_E SUBParser::mParse(int & i4Offset)
{
    ALOGD("i4offset is %d\n, here mSUBFile is 0x%x\n", i4Offset, mSUBFile);
    void * pvTempBuf = NULL;
    pvTempBuf = malloc(20);
    SUB_PARSE_STATE_E eState = SUB_PARSE_FAIL;
    if (NULL == pvTempBuf)
    {
        return eState;
    }

    do
    {
        fseek(mSUBFile, i4Offset, SEEK_SET);
        /************STEP 1 Skip 14 bytes (MPEG2 Packet Start Code) directly*****************/
        // SKIP first 14 bytes as PS heading, gonna reach PES heading
        // 00 00 01 BA XX XX ... XX
        fread(pvTempBuf, 1, MPEG2_PACKET_START_CODE_LENGTH, mSUBFile);
        ALOGD("[RY] first 4 %x %x %x %x", *((char *)pvTempBuf + 1), *((char *)pvTempBuf + 2), *((char *)pvTempBuf + 3), *((char *)pvTempBuf + 4));

        /************STEP 2 Skip 4 bytes (PES Start Code 3 bytes plus PES Stream ID 1 byte)*******************/
        // PES header
        // 00 00 01 BD
        fread(pvTempBuf, 1, MPEG2_PES_START_CODE_LENGTH + MPEG2_PES_STREAM_ID_LENGTH, mSUBFile);
        //ALOGE("[MY] --- PES 4 bytes : 0x%x", *((int *)pTempBuf));


        /************STEP 3 Get PES Packet Length************************/
        // 0x5906 -> 0x0659  Little Endian -> Big Endian
        // It seems the tablet system is running on Little Endian, but the .sub file should be read on Big Endian
        fread(pvTempBuf, 1, 2, mSUBFile);
        int iPESPacketTotalLength = readBigEndian((char *)pvTempBuf, MPEG2_PES_SIZE_FLAG_LENGTH);
        ALOGD("[RY] --- iPESPacketTotalLength : 0x%x\n", iPESPacketTotalLength);


        /****NOTE****/
        /****Use the same method as BDP ****/
        /****A new PES Packet must have PTS present ****/

        /************STEP 4  Judge if current PES packet is new**********/
        // sample 0x 81 80
        fread(pvTempBuf, 1, 2, mSUBFile);
        char cFlag = *((char *)pvTempBuf + 1);
        bool fgIsNewPESPacket = 0x02 == (cFlag >> 6 & 0x03) ?
                                true : false;
        ALOGD("[RY] fgIsNewPESPacket is %d\n", fgIsNewPESPacket);

        /************STEP 5************************/
        fread(pvTempBuf, 1, 1, mSUBFile);
        char * pcRemainHeaderLength = (char *)pvTempBuf;

        unsigned short uRemainHeaderLength = (unsigned short)(* pcRemainHeaderLength);
        ALOGD("uRemainHeaderLength %d\n", uRemainHeaderLength);
        //reamin length plus 1 as PES header is followed by a PES stream id flag, occupying 1 byte
        fseek(mSUBFile, uRemainHeaderLength + 1, SEEK_CUR);

        /************STEP 6************************/
        if (fgIsNewPESPacket)
        {
            fread(pvTempBuf, 1, 2, mSUBFile);
            fseek(mSUBFile, -2, SEEK_CUR);

            int iSubtitlePacketLength = readBigEndian((char *)pvTempBuf, 2);
            ALOGD("subtitlePacketLength is %d\n", iSubtitlePacketLength);
            if (iSubtitlePacketLength > 0)
            {
                m_rSpData.m_pvSubtitlePacketBuffer = malloc(iSubtitlePacketLength);
                if (m_rSpData.m_pvSubtitlePacketBuffer != NULL)
                {
                    m_rSpData.m_iLength = (unsigned int)iSubtitlePacketLength;
                    m_rSpData.m_iCurrentOffset = 0;
                }
                else
                {
                    eState = SUB_PARSE_FAIL;
                    break;
                }

            }
            else
            {
                ALOGD("SubtitlePacket Length %d is invalid\n", iSubtitlePacketLength);
                eState = SUB_PARSE_FAIL;
                break;
            }
        }
        // Not a new one
        else
        {
            ALOGD("m_rSpData.m_pvSubtitlePacketBuffer %p \n",m_rSpData.m_pvSubtitlePacketBuffer);
            if (NULL == m_rSpData.m_pvSubtitlePacketBuffer)
            {
                ALOGD("fgIsNewPESPacket %d is invalid at offset 0\n");
                eState = SUB_PARSE_FAIL;
                break;
            }
            ALOGD("Not a new one, iPESPacketTotalLength is %d\n", iPESPacketTotalLength);
            ALOGD("Sp Data Length is %d, current offset is %d\n", m_rSpData.m_iLength, m_rSpData.m_iCurrentOffset);
        }

        /**********STEP 7*************************/

        int iReadSize = 0;
        bool fgIsFinished = false;

        //2, fixed part of optional PES header, 2 bytes
        //1, reamin header length flag, 1 byte
        //1, stream language flag, 1 byte
        //the calculate result is the size of subtitle data and padding data saved in current MPEG packet
        if (iPESPacketTotalLength - 2 - 1 - uRemainHeaderLength - 1 >= m_rSpData.m_iLength - m_rSpData.m_iCurrentOffset)
        {
            //we can finish filling sp data buffer
            iReadSize = m_rSpData.m_iLength - m_rSpData.m_iCurrentOffset;
            fgIsFinished = true;
            ALOGD("iReadSize is %d\n", iReadSize);
        }
        else
        {
            iReadSize = iPESPacketTotalLength - 2 - 1 - uRemainHeaderLength - 1;
            fgIsFinished = false;
            ALOGD("iReadSize is %d, 2\n", iReadSize);
        }



        char * temp = m_rSpData.m_iCurrentOffset + (char *)m_rSpData.m_pvSubtitlePacketBuffer;
        fread(temp, 1, iReadSize, mSUBFile);

        ALOGD("m_iCurrentOffset %d, temp[0] %x, temp[1] %x\n", m_rSpData.m_iCurrentOffset, *temp, *(temp + 1));
        m_rSpData.m_iCurrentOffset += iReadSize;

        if (fgIsFinished)
        {
            eState = SUB_PARSE_DONE;
        }
        else
        {
            eState = SUB_PARSE_NEXTLOOP;
        }


        i4Offset = (int)ftell(mSUBFile);

    }
    while (false);

    free(pvTempBuf);

    ALOGD("eState is %d\n", eState);
    return eState;

}

unsigned int SUBParser::readBigEndian(char *pByte, int bytesCount)
{
    char *pTemp = pByte;
    unsigned int sum = 0;
    while (bytesCount-- > 0)
    {
        sum <<= 8; // move 8 bits to left
        sum += *((unsigned char *)(pTemp));

        ++pTemp;

    }
    return sum;
}
bool SUBParser::fgIsBufferReady()
{
    return true;//m_SpParser->fgIsBufferReady();
}

}


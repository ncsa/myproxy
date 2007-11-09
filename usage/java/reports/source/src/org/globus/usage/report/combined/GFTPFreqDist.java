/*
 * Copyright 1999-2006 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globus.usage.report.combined;

import org.globus.usage.report.common.DatabaseRetriever;
import org.globus.usage.report.common.HistogramParser;
import org.globus.usage.report.common.IPEntry;
import org.globus.usage.report.common.TimeStep;

import java.sql.ResultSet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;

public class GFTPFreqDist extends SlotLogHelper {

    static class FreqDist {
        public int numPackets;
        public long firstPacket;
        public long lastPacket;
        public long busyness;

        public FreqDist(long sendTime)
        {
            this.numPackets = 0;
            this.firstPacket = sendTime;
            this.lastPacket = sendTime;
            this.busyness = 0;
            this.addSendTime(sendTime);
        }

        public void addSendTime(long sendTime)
        {
            if (sendTime < this.firstPacket)
            {
                this.firstPacket = sendTime;
            }
            if (sendTime > this.lastPacket)
            {
                this.lastPacket  = sendTime;
            }
            this.numPackets++;
        }
    }

    public GFTPFreqDist()
    {
        initializeSlots();
    }

    public void computeAvgTimeDiff(FreqDist fDist, String ipAddr)
    {
        long diffmSecs = (fDist.lastPacket - fDist.firstPacket);
        long diffSecs = (diffmSecs / 1000);
        if (diffSecs >= SEC_PER_MONTH)
        {
            long diffSecsPerWeek = (diffSecs / SEC_PER_WEEK);
            long diffSecsPerDay = (diffSecs / SEC_PER_DAY);
            fDist.busyness = (fDist.numPackets / diffSecsPerDay);

            System.out.println("<data ip=\"" + ipAddr + "\" num_packets=\"" +
                               fDist.numPackets + "\" weeks_apart=\"" +
                               diffSecsPerWeek + "\" DaysBetweenPackets=\"" + diffSecsPerDay +
                               "\" busyness=\"" + fDist.busyness + "\"/>");

            Slot slot = getSlot(fDist.busyness);
            slot.increment();
        }
    }

    public static void main(String[] args) throws Exception {
        String USAGE = "Usage: java GFTPFreqDist [options] <date (YYYY-MM-DD)> Enter -help for a list of options\n";

        String HELP = "Where [options] are:\n"
                + " -help                 Displays help\n"
                + " -step <day|month>     Specifies size of step (day by default)\n"
                + " -n <steps>            Specifies number of steps to do\n";

        if (args.length == 0) {
            System.err.println(USAGE);
            System.exit(1);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("-help")) {
            System.err.println(USAGE);
            System.err.println(HELP);
            System.exit(1);
        }

        int n = 1;
        String stepStr = "day";
        String baseQueryStart = "select send_time,hostname from ";
        String table = "gftp_packets";
        String baseQueryEnd = " where ";

        String inputDate = args[args.length - 1];

        // parse step info
        int step = -1;
        if (stepStr.equalsIgnoreCase("day")) {
            step = Calendar.DATE;
        } else if (stepStr.equalsIgnoreCase("month")) {
            step = Calendar.MONTH;
        } else {
            System.err.println("Unsupported step: " + stepStr);
            System.exit(2);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        GFTPFreqDist fDistGraph = new GFTPFreqDist();

        DatabaseRetriever dbr = new DatabaseRetriever();

        System.out.println("<combined-gftpfreqdist-report>");

        Date date = dateFormat.parse(inputDate);

        Calendar calendar = dateFormat.getCalendar();
        if (n < 0) {
            calendar.add(step, n);
            n = -n;
        }

        HashMap ipMap = new HashMap();

        int firstTime = 1;

        /* this loop handles each day at a time for processing gftp packets */
        while(true)
        {
            Date sDate = calendar.getTime();
            calendar.add(step, n);
            Date endDate = calendar.getTime();
            
            String startDateStr = dateFormat.format(sDate);
            String endDateStr = dateFormat.format(endDate);
            String timeFilter = "send_time >= '" + startDateStr + 
                "' and send_time < '" + endDateStr + "'";

            if (endDateStr.equals("2007-09-02"))
            {
                break;
            }

            if (firstTime == 1)
            {
                System.out.println("  <start-date>" + startDateStr + "</start-date>");
                System.out.println("  <end-date>2007-09-01</end-date>");
                firstTime = 0;
            }

            String query = baseQueryStart + table + baseQueryEnd + timeFilter
                + " order by send_time";

            ResultSet rs = dbr.retrieve(query);

            while (rs.next())
            {
                Timestamp tmpDate = rs.getTimestamp(1);
                String ipAddr = rs.getString(2);

                long milliSecs = tmpDate.getTime();

                FreqDist fDist = (FreqDist)ipMap.get((Object)ipAddr);
                if (fDist == null)
                {
                    fDist = new FreqDist(milliSecs);
                    ipMap.put((Object)ipAddr, fDist);
                }
                else
                {
                    fDist.addSendTime(milliSecs);
                }
            }
            rs.close();
        }

        /* analyze the freq dist data here */
        Iterator key = ipMap.keySet().iterator();
        Iterator value = ipMap.values().iterator();

        while(key.hasNext() && value.hasNext())
        {
            String ipAddr = (String)key.next();
            FreqDist fDist = (FreqDist)value.next();
            fDistGraph.computeAvgTimeDiff(fDist, ipAddr);
        }

        fDistGraph.output(System.out);
        dbr.close();
        System.out.println("</combined-gftpfreqdist-report>");
    }
}
/*
 * All GTAS code is Copyright 2016, Unisys Corporation.
 * 
 * Please see LICENSE.txt for details.
 */
package gov.gtas.parsers.paxlst;

import java.util.ListIterator;

import gov.gtas.parsers.edifact.EdifactParser;
import gov.gtas.parsers.edifact.Segment;
import gov.gtas.parsers.exception.ParseException;
import gov.gtas.parsers.paxlst.segment.usedifact.CTA;
import gov.gtas.parsers.paxlst.segment.usedifact.DTM;
import gov.gtas.parsers.paxlst.segment.usedifact.DTM.DtmCode;
import gov.gtas.parsers.paxlst.segment.usedifact.LOC;
import gov.gtas.parsers.paxlst.segment.usedifact.LOC.LocCode;
import gov.gtas.parsers.paxlst.segment.usedifact.PDT;
import gov.gtas.parsers.paxlst.segment.usedifact.PDT.DocType;
import gov.gtas.parsers.paxlst.segment.usedifact.PDT.PersonStatus;
import gov.gtas.parsers.paxlst.segment.usedifact.TDT;
import gov.gtas.parsers.paxlst.segment.usedifact.UNS;
import gov.gtas.parsers.util.FlightUtils;
import gov.gtas.parsers.vo.ApisMessageVo;
import gov.gtas.parsers.vo.DocumentVo;
import gov.gtas.parsers.vo.FlightVo;
import gov.gtas.parsers.vo.PassengerVo;
import gov.gtas.parsers.vo.ReportingPartyVo;

public final class PaxlstParserUSedifact extends EdifactParser<ApisMessageVo> {
    public PaxlstParserUSedifact() { 
        this.parsedMessage = new ApisMessageVo();
    }

    @Override
    protected String getPayloadText() throws ParseException {
        return lexer.getMessagePayload("CTA", "UNT");
    }

    @Override
    public void parsePayload() throws ParseException {
	CTA cta = getMandatorySegment(CTA.class);
	processReportingParty(cta);
        for (;;) {
		cta = getConditionalSegment(CTA.class);
		if (cta == null) {
			break;
		}
		processReportingParty(cta);
        }

        TDT tdt = getMandatorySegment(TDT.class);
        processFlight(tdt);
        for (;;) {
		tdt = getConditionalSegment(TDT.class);
		if (tdt == null) {
			break;
		}
		processFlight(tdt);
        }

        getMandatorySegment(UNS.class);

        PDT pdt = getMandatorySegment(PDT.class);
        processPax(pdt);
        for (;;) {
		pdt = getConditionalSegment(PDT.class);
		if (pdt == null) {
			break;
		}
		processPax(pdt);
        }
    }

    private void processFlight(TDT tdt) {
        FlightVo f = new FlightVo();
        parsedMessage.addFlight(f);

        f.setFlightNumber(FlightUtils.padFlightNumberWithZeroes(tdt.getC_flightNumber()));
        f.setCarrier(tdt.getC_airlineCode());
    }

    private void processFlight(Segment seg, ListIterator<Segment> i) {
        TDT tdt = (TDT)seg;
        FlightVo f = new FlightVo();
        parsedMessage.addFlight(f);

        f.setFlightNumber(FlightUtils.padFlightNumberWithZeroes(tdt.getC_flightNumber()));
        f.setCarrier(tdt.getC_airlineCode());

        while (i.hasNext()) {
            Segment s = i.next();
//            System.out.println("\t" + s);
            switch (s.getName()) {
            case "LOC":
                LOC loc = (LOC)s;
                LocCode locCode = loc.getLocationCode();
                String country = loc.getIataCountryCode();
                String  airport = loc.getIataAirportCode();
                if (locCode == LocCode.DEPARTURE) {
//                    f.setOriginCountry(country);
                    f.setOrigin(airport);
                } else if (locCode == LocCode.ARRIVAL) {
//                    f.setDestinationCountry(country);
                    f.setDestination(airport);
                }
                break;
            
            case "DTM":
                DTM dtm = (DTM)s;
                DtmCode dtmCode = dtm.getDtmCode();
                if (dtmCode == DtmCode.DEPARTURE_DATETIME) {
                    f.setEtd(dtm.getC_dateTime());
                } else if (dtmCode == DtmCode.ARRIVAL_DATETIME) {
                    f.setEta(dtm.getC_dateTime());
                }
                break;
                
            default:
                i.previous();
                return;
            }
        }
    }

    private void processPax(PDT pdt) {
        PassengerVo p = new PassengerVo();
        parsedMessage.addPax(p);

        p.setFirstName(pdt.getLastName());
        p.setLastName(pdt.getLastName());
        p.setMiddleName(pdt.getC_middleNameOrInitial());
        p.setDob(pdt.getDob());
        p.setGender(pdt.getGender());
        PersonStatus status = pdt.getPersonStatus();
        p.setPassengerType(status.toString());
        if (status == PersonStatus.CREW) {
            p.setPassengerType("C");
        } else if (status == PersonStatus.IN_TRANSIT){
            p.setPassengerType("I");
        } else {
            p.setPassengerType("P");
        }

        DocumentVo d = new DocumentVo();
        p.addDocument(d);
        d.setDocumentNumber(pdt.getDocumentNumber());
        d.setExpirationDate(pdt.getC_dateOfExpiration());
        DocType docType = pdt.getDocumentType();
        d.setDocumentType(docType.toString());
        if (docType == DocType.PASSPORT) {
            d.setDocumentType("P");
        } else if (docType == DocType.VISA) {
		d.setDocumentType("V");
        }
//        System.out.println("\t" + p);
    }

    private void processReportingParty(CTA cta) {
        ReportingPartyVo rp = new ReportingPartyVo();
        parsedMessage.addReportingParty(rp);
        rp.setPartyName(cta.getName());
        rp.setTelephone(cta.getTelephoneNumber());
        rp.setFax(cta.getFaxNumber());
    }
}

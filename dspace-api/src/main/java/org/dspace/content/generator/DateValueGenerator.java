package org.dspace.content.generator;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.util.DateMathParser;
import org.dspace.content.Item;
import org.dspace.content.Metadatum;

public class DateValueGenerator implements TemplateValueGenerator {

	Logger log = Logger.getLogger(DateValueGenerator.class);
	@Override
	public Metadatum[] generator(Item targetItem, Item templateItem,
			Metadatum metadatum, String extraParams) {
		
		Metadatum[] m = new Metadatum[1];
		m[0] = metadatum;		
		String[] params = StringUtils.split(extraParams, "\\.");
		String operazione="";
		String formatter="";
		
		Date date = new Date();
		DateMathParser dmp = new DateMathParser();
		String value=""; 
		if(params != null && params.length >1){
			operazione= params[1];
			formatter= params[0];
			try {
				date = dmp.parseMath(operazione);
			} catch (ParseException e) {
				log.error(e.getMessage(), e);
			}finally{
				DateFormat df = new SimpleDateFormat(formatter);
				value = df.format(date);
			}
		}else if(params.length == 1){
			formatter= params[0];
			DateFormat df = new SimpleDateFormat(formatter);
			value = df.format(date);
		}else {
			value = date.toString();
		}
		
		metadatum.value= value;
		return m;
	}


}

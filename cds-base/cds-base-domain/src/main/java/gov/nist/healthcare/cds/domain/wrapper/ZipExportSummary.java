package gov.nist.healthcare.cds.domain.wrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import gov.nist.healthcare.cds.domain.xml.ErrorModel;

public class ZipExportSummary  extends ExportSummary {

	
	private ByteArrayOutputStream baos;
	private ZipOutputStream out;
	final int BUFFER = 2048;
	
	
	public ZipExportSummary() {
		super();
	}


	public ZipExportSummary(ByteArrayOutputStream out) {
		super();
		baos = out;
		this.out = new ZipOutputStream(out);
		testCases = new ArrayList<EntityResult>();
	}


	public int getAll() {
		return all;
	}


	public void setAll(int all) {
		this.all = all;
	}

	public List<EntityResult> getTestCases() {
		return testCases;
	}


	public ByteArrayOutputStream getBaos() {
		return baos;
	}


	public void setBaos(ByteArrayOutputStream baos) {
		this.baos = baos;
	}


	public void setTestCases(List<EntityResult> testCases) {
		this.testCases = testCases;
	}

	public int getWerrors() {
		return werrors;
	}


	public void setWerrors(int werrors) {
		this.werrors = werrors;
	}


	public ZipOutputStream getOut() {
		return out;
	}


	public void setOut(ZipOutputStream out) {
		this.out = out;
	}


	public void add(ExportResult toAdd,List<String> names) throws IOException{
		for(ExportedFileStream efs : toAdd.getReader()){
			byte data[] = new byte[BUFFER];
			this.resultFor(efs.getName());
			String name = uniqueName(efs.getName(), names);
			ZipEntry e = new ZipEntry(name);
			out.putNextEntry(e);
			int size = -1;
			while((size = efs.getIn().read(data, 0, BUFFER)) != -1  )
            {
                out.write(data, 0, size);
            }
			names.add(name);
			out.closeEntry();
			all++;
		}
	}
	
	/**
	 * Makes an entry name unique within the archive by inserting a numeric
	 * suffix before the extension : {@code name.xml}, {@code name_2.xml} and
	 * so on.
	 *
	 * This used to prepend {@code *} per collision, which produced entries
	 * that Windows and several archive tools refuse to extract, so the files
	 * silently went missing from the unpacked folder. The formatter normally
	 * hands over names that are already unique; this is the backstop for the
	 * case where two formatters, or two batches, meet in one archive.
	 */
	static String uniqueName(String name, List<String> taken){
		if(!taken.contains(name)){
			return name;
		}
		int dot = name.lastIndexOf('.');
		String stem = dot == -1 ? name : name.substring(0, dot);
		String extension = dot == -1 ? "" : name.substring(dot);
		int counter = 1;
		String candidate;
		do {
			counter++;
			candidate = stem + "_" + counter + extension;
		} while(taken.contains(candidate));
		return candidate;
	}

	public void invalid(String name, List<ErrorModel> errors){
		EntityResult rs = this.resultFor(name);
		rs.setErrors(errors);
		werrors++;
		all++;
		
	}
	
	public EntityResult resultFor(String name){
		for(EntityResult r : this.getTestCases()){
			if(r.getName().equals(name))
				return r;
		}
		EntityResult r = new EntityResult();
		r.setName(name);
		this.getTestCases().add(r);
		return r;
	}
}

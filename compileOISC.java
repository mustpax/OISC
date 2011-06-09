import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Converts a file written in Paksoy Kader OISC Programming Language (PKOPL) into
 * binary code that runs on our OISC chip. The output files are readable by the VHDL
 * compiler.
 * <br>
 * Run using: <tt>java compileOISC &lt;source text file&gt; [output file]</tt>
 * <br>If output file is not specified name "compiled.mif" is assumed
 * <br><br>
 * 
 * <h3>PKOPL Syntax and Operators</h3>
 * PKOPL is a simple programming language allows variable definitions. It uses only
 * a single instruction in implementing its capabilities. It is a good idea to perform
 * all operations on variables since almost all instructions only take memory addresses
 * as arguments. In fact, the only instruction that takes immediate values is DEF (define). 
 * 
 * <h4>Operators</h4>
 * <ul>
 * <li> <tt>JMP m:</tt> jump to memory loca tion m 
 * <li> <tt>JMPI a:</tt> jump to memory location stored in address a (m(a)) 
 * <li> <tt>SUB a b c:</tt> m(c) = m(b) - m(a)
 * <li> <tt>ADD a b c:</tt> m(c) = m(a) + m(b)
 * <li> <tt>DIV a b c:</tt> m(c) = m(a) / m(b)
 * <li> <tt>MUL a b c:</tt> m(c) = m(a) * m(b)
 * <li> <tt>IFGT a b c:</tt> IF a&gt;b THEN JMP c
 * <li> <tt>IFLE a b c:</tt> IF a&lt;=b THEN JMP c
 * <li> <tt>DEF x <i>constant</i>:</tt> define variable 'x' to be <i>constant</i>
 * <li> <tt>MOV a b:</tt> move m(a) to m(b)
 * </ul>
 * <h4>Syntax</h4>
 * <b>General</b>
 * <br>
 * Variable definitions need to be placed in the beginning of a file.
 * <br>Only one operation is read from each line, everything else is ignored. So
 * comments can be placed on lines after all operands.
 * <br> Variables values are parsed as 8-bit two's complement binary integers.
 * These values are only used for generating the initial RAM content. In the compiler
 * the variables are parsed as the memory address reserved to them. To create a pointer
 * define a variable x, then define another variable y as x. This will use the address
 * of x as the value for y.
 * <br><i>Example:</i>
 * <br><tt>DEF A 10</tt> -Address FE is allocated to A, contents of the address will be 
 * initialized with 0A (number 10)
 * <br><tt>DEF B A</tt>  -Address FD is allocated to B, contents of the address will be
 * initialized with FE (address of A)
 * <br>Addresses are 8-bit unsigned binary integers.
 * <br>
 * <b>Constant expressions:</b>
 * <ul>
 * <li> Hex form: preceed by '$' <i>ex:</i> $F0C1
 * <li> Binary form: preceed by '#'. <i>ex:</i> #1000100
 * <li> Decimal form: any operand consisting only of numbers. <i>ex:</i> 1231
 * </ul>
 *  
 * <br>
 * <i>
 * <br>Multiply and divide OISC algorithms based on code at 
 * http://www.cse.psu.edu/~cg331/samp/OISC/macros
 * <br>Part of project OISCcompiler
 * <br>Created on May 11, 2005, using eclipse 3.1 
 * <br>Written in Java 5
 * </i>
 * 
 * @author Paksoy Kader
 */

public class compileOISC {
    private int heapPt, pCount;
    /**Definitions end flag*/
    private boolean defEnd;
    
    /**Variable lookup table*/
    private Hashtable<String,variable> variables;
    
    /**
     * theInstruction is the normal instruction that uses direct addressing for all
     * parameters
     * <br>loadInstruction take a parameter as immediate value and stores it at b,
     * jumps to c if b<=0
     * <br>cr is the line terminator for this platform, courtesy of  
     * http://www.javapractices.com/Topic42.cjp
     */
    public static String theInstruction = "0", cr=System.getProperty("line.separator"),
                loadInstruction = "1";
    /**maximum number of variables allowed*/
    private static int maxVar = 100;
    /**heapPtStart is the lowest point of the heap*/
    private static Integer heapPtStart = 249;
    
    /**Reserved memory addresses for registers*/
    public static String negOneRegister="11111110",tempAReg="11111101",tempBReg="11111100",
        tempCReg="11111011",zeroRegister="11111010";
    
    /**Constructor initializes local variables*/
    compileOISC() {
        heapPt = heapPtStart;
        pCount = 0;
        defEnd = false;
        variables = new Hashtable<String,variable>();
    }

    /**Private variable class represents variables*/
    private class variable {
        private int memLoc,value;
        private String memLocBin, valueBin, name;
        
        variable(String nname, int mem, int val) {
            value = val;
            memLoc = mem;
            memLocBin = decToBin(mem);
            name = nname;
        }
        
        public String getMemLocBin() {
            return memLocBin;
        }
        
        public int getMemLoc() {
            return memLoc;
        }
        
        public void setMemLoc(int newloc) {
            memLoc = newloc;
            memLocBin = decToBin(newloc);
        }
        
        public int getValue() {
            return value;
        }
        
        public void setValue(byte newval) {
            value = newval;
        }

        public String getName() {
            return name;
        }
        
        public void setName(String nname){
            name = nname;
        }
    }
    
    /**
     * Expand hex representation to binary
     * @param hex number in hex form, should be 2 chars
     * @return number in 8-bit binary form
     */
    public static String hexToBin(String hex) {
        char[] hexRep = hex.toCharArray();
        int numchars = hexRep.length;
        
        //check input length
        if (numchars>2) {
            System.out.println("compileOISC:hexToBin:hex representation too long, " +
                    "high bits will be discarded. Input: \""+hex+"\".");
            numchars=2;
        }
            
        else if (numchars<2)
            System.out.println("compileOISC:hexToBin:hex representation too short, " +
                    "leading 0's will be added. Input: \""+hex+"\".");
        
        StringBuffer binaryRep = new StringBuffer();
        for (int i=0;i<numchars;i++) {
            //First convert hex representation to integer and then retreive in
            //binary form
            String singlehex = Integer.toBinaryString(
                    Integer.parseInt(((Character) hexRep[i]).toString(),16));
            
            //fill in missing leading zero's
            for (int j=singlehex.length();j<4;j++)
                singlehex = "0"+singlehex;
            
            //append to result string
            binaryRep.append(singlehex);
        }
        
        for (int i=numchars;i<2;i++)
            binaryRep.insert(0,"0000");
        
        return binaryRep.toString();
    }
    
    /**Convert integer to its 8bit unsigned binary representation*/
    public static String decToBin (int dec) {
        //low bound
        if (dec<=0)
            return "00000000";
        
        //upper bound 
        else if (dec>=255) 
            return "11111111";
        
        else {
            //generate binary string
            String ret = Integer.toBinaryString(dec);
            int retlen = ret.length();
            
            //fill in preceding zeros if necessary
            for (int i=retlen;i<8;i++)
                ret = "0" + ret;
            
            //discard unnecessary preceding zeros
            ret = ret.substring((ret.length()-8));
            return ret;
        }
    }
    
    /**
     * Make given binary number 8 bits long, by either padding with leading
     * or truncating high bits.
     * 
     * @param bin input binary number
     * @return 8-bit long binary number 
     */
    public static String binToBin(String bin) {
        String ret = bin;

        //fill in preceeding zeros
        for (int i=bin.length();i<8;i++)
            ret = "0" + ret;
        
        //discard unnecessary preceding zeros
        ret = ret.substring((ret.length()-8));
        
        return ret;
    }
        
    /**Compiles given source code file, and writes the result to
     * given target file
     * @param inputFile file that contains source
     * @param targetFile file to write to
     */
    public static void compile(String inputFile, String targetFile) {
        System.out.println("Reading source from file: "+inputFile);
        int counter = 0;

        //Scanner reads input file
        Scanner source = null;
        try {
            source = new Scanner(new FileInputStream(inputFile));
        } catch (FileNotFoundException e) {
            System.out.println("Cannot find file: "+inputFile);
            System.exit(0);
        }

        //create instance of compiler class to start reading
        compileOISC compiler = new compileOISC();

        //file write library usage code taken from: 
        //http://www.javapractices.com/Topic42.cjp 
        Writer output = null;
        try {
            StringBuffer  sourceoutput = new StringBuffer();
            output = new BufferedWriter( new FileWriter(targetFile) );
            
            //write mif headers 
            output.write("DEPTH = 256;");
            output.write(cr);
            output.write("WIDTH = 25;"+cr+
                    "ADDRESS_RADIX = BIN;"+cr+ 
                    "DATA_RADIX = BIN;"+cr+ 
                    "CONTENT"+cr+ 
                    "BEGIN"+cr);
            
            //load useful values
            sourceoutput.append(compiler.loadRefVals());
            
            //Read all lines
            while (source.hasNextLine()) 
                sourceoutput.append(compiler.compileLine(source.nextLine(),
                        counter++));
            
            //write result
            output.write(sourceoutput.toString());
            
            //fill lines upto 256
            for (int i = compiler.getPCount();i<256;i++)
                output.write(decToBin(i)+" : 0000000000000000000000000 ;"+cr);
            
            //write end mark
            output.write("END;"+cr);
            
        } catch (IOException e) {
            System.out.println("compileOISC:compile:error when writing to " +
                    "file.");
            System.exit(0);
        }
        finally {
          //flush and close both "output" and its underlying FileWriter
          if (output != null)
            try {
                output.close();
            } catch (IOException e) {
                System.out.println("compileOISC:compile:error whilr closing " +
                        "output stream.");
                System.exit(0);
            }
        }
        
        System.out.println("Finished compiling file "+inputFile+" "+
                counter+" lines read.");
    }
    
    /**Accessor fir program counter*/
    public int getPCount(){
        return pCount;
    }
    
    /**Compiles a given line of source code.
     * <br>Each line can only contain one operator. Operands are extracted
     * and passed onto seperatemethods that handle different operations, where
     * they are parsed further. 
     * 
     * @param input string to compile
     * @return String assembly code for this line
     */
    private String compileLine(String input, int linenum) {
        Scanner linereader = new Scanner(input);

        //check if line empty
        if (!linereader.hasNext())
            return "";
        
        String oper = linereader.next();
        StringBuffer ret = new StringBuffer();
        
        //parse operator 
        try {
            if (oper.equals("DEF"))
                ret.append(define(linereader.next(),
                        Byte.parseByte(linereader.next())));
            
            else if (oper.equals("JMP"))
                ret.append(jump(linereader.next()));
            
            else if (oper.equals("ADD")) 
                ret.append(add(linereader.next(),linereader.next(),
                        linereader.next()));
            
            else if (oper.equals("SUB")) 
                ret.append(sub(linereader.next(),linereader.next(),
                        linereader.next()));
                
            else if (oper.equals("DIV")) 
                ret.append(divide(linereader.next(),linereader.next(),
                        linereader.next()));
                
            else if (oper.equals("MUL"))
                ret.append(multiply(linereader.next(),linereader.next(),
                        linereader.next()));
                
            else if (oper.equals("IFGT")) 
                ret.append(ifgt(linereader.next(),linereader.next(),
                        linereader.next()));                
            
            else if (oper.equals("IFLE")) 
                ret.append(ifle(linereader.next(),linereader.next(),
                        linereader.next()));                
            
            else if (oper.equals("MOV")) 
                ret.append(move(linereader.next(),linereader.next()));
            
            //parse failed
            else {
                System.out.println("compileOISC:compileLine:Cannot parse operator \""
                        +oper+"\" on line " +linenum+", skipping line.");
                return "";
            }
        } catch (NoSuchElementException e) {
            System.out.println("compileOISC:compileLine:need more operands for " +
                    oper+ " on line "+linenum+".");
        }
        
        return ret.toString();
    }
    
    /**
     * Creates a variable and reserves it space in the heap,
     * if maximum number of variables is exceeded displays error message.
     * 
     * @param varname name of variable to create
     * @param val value to initially assign to variable
     * @return instruction to initialize mem loc with val
     */
    private String define(String varname, int val) {
        if (defEnd) {
            System.out.println("compileOISC:define:" +
                    "can only define variables at beginning of file.");
            return "";
        }

        //check if variable name is only composed of numbers
        else if (Pattern.matches("\\d+",varname)) {
            System.out.println("compileOISC:define:" +
                    "invalid variable name "+varname+" must also contain non-numeric " +
                    "characters.");
            return "";
        }
        //check if variable already exists
        variable oldvar = variables.get(varname);
        if (oldvar==null) {
            //check if number of max variables is exceeded
            if ((heapPtStart-heapPt)>=maxVar) {
                System.out.println("compileOISC:define:out of heap space," +
                        " maximum number of variables exceeded. cannot define" +
                        " new variable "+varname+".");
                return "";
            }
            
            //create new variable object
            variable nvar = new variable(varname,heapPt,val);
            String ret = loadABC(intToBin(val),parseOperand(heapPt)).
                    toString();
            //put new variable in hashtable
            variables.put(varname,nvar);
            //move heapPt down
            heapPt--;
            return ret;
        }
        //if variable already exists redefine
        if (varname.equals("ioPort")) {
            System.out.println("compileOISC:define:" +
            "ioPort is a reserved variable name.");
            return "";
        }
        variables.put(varname,new variable(varname,oldvar.getMemLoc(),
                val));
        
        return loadABC(intToBin(val),oldvar.getMemLocBin()).
                toString();
    }
    
    /**Load useful values*/
    private StringBuffer loadRefVals() {
        StringBuffer ret = new StringBuffer();
        
        ret.append(loadABC("00000000",zeroRegister));
        ret.append(loadABC("11111111",negOneRegister));
        ret.append(loadABC("00000000",tempAReg));
        ret.append(loadABC("00000000",tempBReg));
        ret.append(loadABC("00000000",tempCReg));
        
        return ret;
    }
    
    
    /**
     * Generates assembly code that adds two variables and stores
     * @param a first variable
     * @param b second variable
     * @param c dest variable
     * @return source code that does c = a + b
     */
    private StringBuffer add(String a, String b, String c) {
        if (!defEnd)
            defEnd = true;
        StringBuffer ret = new StringBuffer();
        String operA = parseOperand(a);
        String operB = parseOperand(b);
        String operC = parseOperand(c);
        
        //if parse fails for any of the operands skip line
        if ((operA==null)||(operB==null)||(operC==null))
            return new StringBuffer();
        
        //generate assembly code
        //combine variables in temporary register
        ret.append(insABC(operA,zeroRegister));
        ret.append(insABC(operB,zeroRegister));
        //clear destination and store
        ret.append(insABC(operC,operC));
        ret.append(insABC(zeroRegister,operC));
        //clear zero register
        ret.append(insABC(zeroRegister,zeroRegister));

        return ret;
    }
    
    /**
     * Generates assembly code that stores the result of
     * subtracting a from b, to b
     * @param a first variable
     * @param b second variable
     * @param c destination var
     * @return source code that does c = b - a
     */
    private StringBuffer sub(String a, String b, String c) {
        if (!defEnd)
            defEnd = true;
        StringBuffer ret = new StringBuffer();
        String operA = parseOperand(a);
        String operB = parseOperand(b);
        String operC = parseOperand(c);
        
        //if parse fails for any of the operands skip this line
        //of source
        if ((operA==null)||(operB==null)||(operC==null))
            return new StringBuffer();
            
        //generate assembly code
        ret.append(clearReg(tempAReg));
        ret.append(clearReg(tempBReg));
        //ta = -a
        ret.append(insABC(operA,tempAReg));
        //tb = -b
        ret.append(insABC(operB,tempBReg));
        //ta = b-a
        ret.append(insABC(tempBReg,tempAReg));
        ret.append(insABC(operC,operC));
        ret.append(clearReg(tempBReg));
        ret.append(insABC(tempAReg,tempBReg));
        ret.append(insABC(tempBReg,operC));
        
        return ret;
    }
    
    /**
     * Generates assembly code that jumps to a
     * 
     * @param a jump loacta?on
     * @return jump to a
     */
    private StringBuffer jump(String a) {
        if (!defEnd)
            defEnd = true;
        String operA = parseOperand(a);
        
        //if operand doesn't parse skip line
        if (operA==null)
            return new StringBuffer();
        
        pCount++;
        return insABC(zeroRegister,zeroRegister,operA);
    }
    
    /**
     * Jump indirect: jump to location in m(a)
     * 
     * @param a pointer location
     * @return jump to m(a)
     */
    private StringBuffer jumpi(String a) {
        if (!defEnd)
            defEnd = true;
        String operA = parseOperand(a);
        
        //if operand doesn't parse skip line
        if (operA==null)
            return new StringBuffer();
        
        StringBuffer ret = new StringBuffer();
        
        //zr=-m(a)
        ret.append(insABC(operA,zeroRegister));
        //tempa=0
        ret.append(insABC(tempAReg,tempAReg));
        //tempa=m(a)
        ret.append(insABC(zeroRegister,tempAReg));
        //zr=0, jump to m(a)
        pCount++;
        ret.append(insABC(zeroRegister,zeroRegister,
                tempAReg));
        
        return ret;
    }
    
    
    /**
     * m(b) = m(a)
     * @param a mem loc a
     * @param b mem loc b
     * @return assembly code that does m(b) = m(a)
     */
    private StringBuffer move(String a, String b) {
        if (!defEnd)
            defEnd = true;
        StringBuffer ret = new StringBuffer();
        String operA = parseOperand(a);
        String operB = parseOperand(b);
        
        //if parse fails for any of the operands skip this line
        //of source
        if ((operA==null)||(operB==null))
            return new StringBuffer();
            
        //generate assembly code
        //clear destination
        ret.append(insABC(operB,operB));
        ret.append(insABC(operA,zeroRegister));
        ret.append(insABC(zeroRegister,operB));
        //clear zeroRegister
        ret.append(insABC(zeroRegister,zeroRegister));
        
        return ret;
    }
    
    /**
     * Integer division b by a and store result in b  
     * 
     * @param a operand a
     * @param b operand b
     * @param c destination addr c
     * @return m(c) = m(b) / m(a)
     */
    private StringBuffer divide(String a,String b,String c) {
        if (!defEnd)
            defEnd = true;
        
        StringBuffer ret = new StringBuffer();
        String operA = parseOperand(a);
        String operB = parseOperand(b);
        String operC = parseOperand(c);
        
        //if parse fails for any of the operands skip this line
        //of source
        if ((operA==null)||(operB==null)||(operC==null))
            return new StringBuffer();
        
        //Clear tempA
        ret.append(clearReg(tempAReg));
        ret.append(clearReg(tempBReg));
        ret.append(clearReg(tempCReg));
        
        //load temp regs
        //tempb = b
        ret.append(insABC(operB,tempAReg));
        ret.append(insABC(tempAReg,tempBReg));

        //tempc = a
        ret.append(clearReg(tempAReg));
        ret.append(insABC(operA,tempAReg));
        ret.append(insABC(tempAReg,tempCReg));
        ret.append(clearReg(operC));

        //c++
        ret.append(insABC(negOneRegister,operC));
        pCount++;
        ret.append(insABC(tempCReg,tempBReg,
                parseOperand(pCount+1)));
        pCount++;
        ret.append(insABC(zeroRegister,zeroRegister,
                parseOperand(pCount-3)));
           
        return ret;
    }
    
    /**
     * Multiply a by b and store in c  
     * 
     * @param a operand a
     * @param b operand b
     * @param c destination addr c
     * 
     * @return m(c) = m(b) * m(a)
     */
    private StringBuffer multiply(String a,String b,String c) {
        if (!defEnd)
            defEnd = true;
        
        StringBuffer ret = new StringBuffer();
        String operA = parseOperand(a);
        String operB = parseOperand(b);
        String operC = parseOperand(c);
        
        //if parse fails for any of the operands skip this line
        //of source
        if ((operA==null)||(operB==null)||(operC==null))
            return new StringBuffer();
        
        //Clear registers
        ret.append(clearReg(tempAReg));
        ret.append(clearReg(tempBReg));
        ret.append(clearReg(tempCReg));
        
        //tempb = b
        ret.append(insABC(operB,tempAReg));
        ret.append(insABC(tempAReg,tempBReg));
        
        //tempc= 1
        ret.append(insABC(negOneRegister,tempCReg));
        
        //tempa = -a
        ret.append(clearReg(tempAReg));
        ret.append(insABC(operA,tempAReg));
        
        //clear destination
        ret.append(clearReg(operC));
        
        //sub -a from dest and b--, when b<=0 esc
        ret.append(insABC(tempAReg,operC));
        pCount++;
        ret.append(insABC(tempCReg,tempBReg,
                parseOperand(pCount+1)));
        //loop back
        pCount++;
        ret.append(insABC(zeroRegister,zeroRegister,
                parseOperand(pCount-3)));
        
        return ret;
    }
    
    /**
     * Jump to c if m(a)&lt;=m(b)  
     * 
     * @param a operand a
     * @param b operand b
     * @param c jump location
     * @return jmp c if m(a)&lt;=m(b)
     */
    private StringBuffer ifle(String a,String b,String c) {
        if (!defEnd)
            defEnd = true;
        
        String operA = parseOperand(a);
        String operB = parseOperand(b);
        String operC = parseOperand(c);
        
        //if parse fails for any of the operands skip this line
        //of source
        if ((operA==null)||(operB==null)||(operC==null))
            return new StringBuffer();
        StringBuffer ret = new StringBuffer();

        //tempAReg = 0
        ret.append(insABC(tempAReg,tempAReg)); 
        //tempAREg = -a
        ret.append(insABC(operA,tempAReg));
        //tempBReg = 0
        ret.append(insABC(tempBReg,tempBReg)); 
        //tempBReg = a
        ret.append(insABC(tempAReg,tempBReg));
        
        pCount++;
        //tempBReg = a-b, if tempBReg>=0 jmp
        ret.append(insABC(operB,tempBReg,
                operC));
        
        return ret;
    }
    
    /**
     * Jump to c if m(a)>m(b)
     * 
     * @param a operand
     * @param b operand
     * @param c jump location
     * @return branching code
     */
    private StringBuffer ifgt(String a, String b, String c) {
        if (!defEnd)
            defEnd = true;
        
        StringBuffer ret = new StringBuffer();
        String operA = parseOperand(a);
        String operB = parseOperand(b);
        String operC = parseOperand(c);
        
        //if parse fails for any of the operands skip this line
        //of source
        if ((operA==null)||(operB==null)||(operC==null))
            return new StringBuffer();
        
        //tempA = 0
        ret.append(insABC(tempAReg,tempAReg));
        //tempA = -A
        ret.append(insABC(operA,tempAReg));
        //tempB = 0
        ret.append(insABC(tempBReg,tempBReg));
        //tempB = -B
        ret.append(insABC(operB,tempBReg));
        pCount++;
        //tempA = B-A, if tempA<=0 jmp c
        ret.append(insABC(tempBReg,tempAReg,
                operC));
        
        return ret;
    }
    
    /**
     * If operand is a number, assumes decimal representation<br>
     * If constant prefix exists, operand ?s parsed accordingly
     * If neither is true, operand is treated as variable
     *  
     * @param operand raw operand
     * @return 8-bit binary address
     */
    private String parseOperand(String operand) {
        //check if operand needs to be treated as decimal
        if (Pattern.matches("\\d+",operand))
            return decToBin(Integer.parseInt(operand));
        
        //check binary prefix
        else if (operand.startsWith("#"))
            return binToBin(operand.substring(1));
        
        //check hex prefix
        else if (operand.startsWith("$"))
            return hexToBin(operand.substring(1));
        
        //lookup variable from table
        variable var = variables.get(operand);
        //check if variable exists
        if (var==null) {
            System.out.println("compileOISC:parseOperand:" +
                    "cannot find variable: "+operand);
            return null;
        }
        //return address assigned to variable
        return var.getMemLocBin();
    }
    
    /**parseOperand wrapper for handling integers directly*/
    private String parseOperand(int operand) {
        return decToBin(operand);
    }
    
    /**Convert byte value to two's complement binary*/
    public static String intToBin(int operand) {
        //check bounds
        if (operand>=127)
            return "01111111";
        else if (operand<=-127)
            return "10000000";
        else if (operand==0)
            return "00000000";
        else {
            String binRep = "";
            //if number is positive just create binary representation
            //and pad with leading zeros
            if (operand>0) {
                binRep = Integer.toBinaryString(operand);
                
                for (int i=binRep.length();i<8;i++)
                    binRep = "0"+binRep;
            }
            else {
                int diff = operand + 128,i=64;
                
                while (diff!=0) {
                    if (diff>=i) {
                        diff -= i;
                        binRep = binRep+"1";
                    }
                    else
                        binRep = binRep+"0";
                    
                    i = i/2;
                }
                
                binRep = "1"+binRep;
            }
            
            return binRep;
        }
    }

    /**Makes a single theInstruction using given parsed operands*/
    private StringBuffer insABC(String a, String b, String c) {
        StringBuffer ret = new StringBuffer();
        ret.append(decToBin(pCount-1)+" : ");
        ret.append(theInstruction);
        ret.append(a);
        ret.append(b);
        ret.append(c);
        ret.append(" ;"+cr);
        return ret;
    }
    
    /**Wrapper for insABC that assumes jump to next line*/
    private StringBuffer insABC(String a, String b) {
        pCount++;
        return insABC(a,b,parseOperand(pCount));
    }
    
    /**Makes a single loadInstruction using given parsed operands*/
    private StringBuffer loadABC(String a,String b,String c) {
        StringBuffer ret = new StringBuffer();
        //memory address
        ret.append(decToBin(pCount-1)+" : ");
        ret.append(loadInstruction);
        ret.append(a);
        ret.append(b);
        ret.append(c);
        ret.append(" ;"+cr);
        return ret;
    }
    
    /**Wrapper for loadABC that assumes jump to next line*/
    private StringBuffer loadABC(String a, String b) {
        pCount++;
        return loadABC(a,b,parseOperand(pCount));
    }
    
    /**Returns code that clears given register*/
    private StringBuffer clearReg(String a) {
        return insABC(a,a);
    }
    
    /**Main method handles command line input*/
    public static void main(String[] args) {
        try {
            String sourceFile = args[0];
            String targetFile;
            if (args.length>1) {
                targetFile = args[1];
                System.out.println("Using output file: "+targetFile);
            }
            else {
                targetFile = "compiled.mif";
                System.out.println("Using default output file: "+targetFile);
            }
            
            
            //Compile
            compileOISC.compile(sourceFile,targetFile);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Invalid input, you need to specify source file.");
            System.exit(0);
        }
    }
}

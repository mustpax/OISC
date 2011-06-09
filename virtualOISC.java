import java.io.*;
import java.util.*;

/**
 * This program emulates an OISC chip and runs .mif files created by the
 * compiler.
 * <br> Run using:
 * <tt>java virtualOISC</tt>
 * <br>Memory read from special io address results in user being prompted
 * for input. Write to the same address outputs to screen. 
 * <br>
 * <br>
 * <ul><b>Commands:</b>   
 * <li> <tt>compile &lt;file name&gt;:</tt> compile source code file using compileOISC
 * and load generated rom
 * <li> <tt>load &lt;file name&gt;:</tt> load rom state from specified file
 * <li> <tt>romdump:</tt> dump all contents of rom to screen  
 * <li> <tt>ramdump:</tt> dump all contents of ram to screen 
 * <li> <tt>initram:</tt> initialize ram with immediate load instructions at
 * the beginning of rom, dump resulting ram state
 * <li> <tt>run:</tt> run program in rom
 * <li> <tt>romget &lt;address&gt;:</tt> display instruction in specified address 
 * of rom
 * <li> <tt>ramget &lt;address&gt;:</tt> display contents of the given ram address
 * 
 * </ul>
 * <br>
 * <i>
 * <br>Part of project OISCcompiler
 * <br>Created on May 12, 2005 using eclipse 3.1
 * <br>Written in Java5
 * </i>
 * @author Paksoy Kader
 */

public class virtualOISC {
    private int pCount;
    private Hashtable<Integer,Integer> ram;
    private Hashtable<Integer,instruction> rom;
    
    /**default constructor initializes private variables*/
    virtualOISC() {
        rom = new Hashtable<Integer,instruction>();
        ram = new Hashtable<Integer,Integer>();
        pCount = 0;
    }
    
    /**
     * <i>instruction</i> class represents instructions in rom.
     * <br>Constructor acts as instruction interpreter
     * <br>Public method <i>execute()</i> executes instruction, modifies
     * ram and pCount accordingly  
     */
    private class instruction {
        private boolean isLoad, isEmpty;
        private int a, b, c;
        
        /**Constructor parses binary string
         * 
         * @param instr instruction as string of 0s 1s
         */
        instruction(String instr) {
            isEmpty = (instr.equals("0000000000000000000000000"));
            
            //divide instruction into fields
            String opCode = instr.substring(0,1); 
            String operA = instr.substring(1,9);
            String operB = instr.substring(9,17);
            String operC = instr.substring(17);
            
            isLoad = (opCode.equals(compileOISC.loadInstruction));
            
            //for load instruction, A is signed
            if (isLoad)
                a = valToInt(operA);
            else
                a = binToInt(operA);
            
            //parse addresses
            b = binToInt(operB);
            c = binToInt(operC);
        }
        
        public void execute() {
            if (isEmpty) {
                pCount++;
                return;
            }
                    
            //check if load instr
            if (isLoad) {
                //load a into mem loc b
                ram.put(b,a);
            }
            else {
                int operA = ram.get(a);
                int operB = ram.get(b);
                operB -= operA;
                
                //emulate overflow behavior
                if (operB<-128)
                    operB += 255;
                else if (operB>127)
                    operB -= 255;
                    
                //update b
                ram.put(b,operB);
            }
                
            //if b<=0 jmp c, otherwise move to next line
            if (ram.get(b)<=0)
                pCount = c;
            else
                pCount++;
            
            return;
        }
        
        /**accessor for isLoad flag*/
        public boolean isLoad() {
            return isLoad;
        }
        
        /**display instruction as string*/
        public String toString() {
            //TODO
            StringBuffer ret = new StringBuffer();
            if (isLoad) 
                ret.append(String.format("loadim %6d",a));
            else 
                ret.append(String.format("subleq m(%3d)",a));
            
            ret.append(String.format(", m(%3d), %3d",b,c));
            
            return ret.toString();
        }
    }
    
    /**Prompts user to enter a string of given maximum length, if
     * negative max length is specified any legth string is accepted
     * 
     * @param max maximum length of string
     * @return entered string
     */
    public static String getString(int max) {
        BufferedReader stdin = new BufferedReader(
                new InputStreamReader(System.in));
        
        String in;
        while (true) {
            try {
                in = stdin.readLine();
                
                if(in.length()==0) {
                    System.out.print("You have entered an empty string, "+
                    "please reenter: ");
                }
                else if((in.length()>max)&&(max>0)) {
                    System.out.print("String longer than "+max+" characters, "+
                    "please reenter: ");
                }
                else
                    break;
            }
            catch (IOException e) {}
        }
        
        return in;
    }
    
    /**Start running virtual machine*/
    public void start() {
        System.out.println("Welcome to One Instruction Set Coputer(OISC) Emulator!");
        System.out.println("Written in Java 5 with eclipse, Spring 2005");
        
        while (true)
            menu();
    }
    
    /**Display menu, parse commands*/
    private void menu() {
        System.out.print("Enter command or type \"commands\" to get a list of" +
                " available commands: ");
        
        String in = getString(-1);
        try {
            if (in.equals("commands")) {
                System.out.println("Available commands:\n" +
                        "compile <filename>: compile specified file using" +
                        " compileOISC and load rom from resulting file\n" +
                        "load <filename>: load rom from specified file\n" +
                        "run: run program currently loaded to rom\n" +
                        "initram: initialize ram by running load instr in rom\n"+
                        "ramdump: display current contents of ram\n" +
                        "romdump: display current contents of rom\n" +
                        "ramget <ram address>: get value stored in ram address\n" +
                        "romget <rom address>: get instruction stored in rom address\n" +
                "quit: end application");
            }
            
            else if (in.startsWith("compile")){ 
                compileOISC.compile(in.substring(in.indexOf(" ")).trim(),
                            "compiled.mif");
                load("compiled.mif");
            }

            else if (in.startsWith("load")) 
                load(in.substring(in.indexOf(" ")).trim());
            
            else if (in.startsWith("ramget")) 
                ramget(Integer.parseInt(in.substring(in.indexOf(" ")).trim()));
            
            else if (in.startsWith("romget")) 
                romget(Integer.parseInt(in.substring(in.indexOf(" ")).trim()));
            
            else if (in.startsWith("run"))
                run();
            
            else if (in.equals("romdump"))
                romdump();
            
            else if (in.equals("ramdump"))
                ramdump();
            
            else if (in.equals("initram")) {
                initram();
                ramdump();
            }
            
            else if (in.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye!");
                System.exit(0);
            }
            else {
                System.out.println("Invalid command.");
            }
        }
        catch (NumberFormatException e) {
            System.out.println("Invalid address, use positive non-binary " +
            "integer.");
        }
    }
    
    /**
     * Load ROM state from specified .mif file. Format readable by the
     * VHDL compiler is assumed.
     * 
     * @param file mif file
     */
    private void load(String file) {
        try {
            int count = 0;
            pCount = 0;
            System.out.println("Loading rom state from file "+file);
            Scanner lineread = new Scanner(new FileInputStream(file));
            String curline = new String();
            
            //flush current rom and ram
            rom = new Hashtable<Integer,instruction>();
            ram = new Hashtable<Integer,Integer>();
            
            //move down to begin line
            while (!curline.equals("BEGIN")) {
                if (lineread.hasNextLine())
                    curline = lineread.nextLine();
                else {
                    System.out.println("File "+file+" not correctly" +
                            " formatted.");
                    return;
                }
            }
            //skip BEGIN line
            curline = lineread.nextLine();
            
            //read till the END; line
            while (!curline.trim().equals("END;")) {
                //seperate line by :
                String[] splitline = curline.split("[:]");
                
                //delete trailing/leading whitespace
                splitline[0] = splitline[0].trim();
                splitline[1] = splitline[1].trim();
                
                //remove colon and everything after it from instr
                splitline[1] = splitline[1].substring(0,
                        splitline[1].indexOf(";")).trim();
                
                //load new instruction to correct address in ROM
                rom.put(binToInt(splitline[0]),new instruction(splitline[1]));
                
                //read line
                if (lineread.hasNextLine()) {
                    curline = lineread.nextLine();
                    count++;
                }
                else {
                    System.out.println("File "+file+" not correctly" +
                            " formatted.");
                    return;
                }
            }
            
            System.out.println("Finished loading rom state, "+count+
                    " rom lines read.");
        } 
        catch (FileNotFoundException e) {
            System.out.println("Cannot find file: "+file);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("File "+file+" not formatted correctly. Aborting load.");
            rom = new Hashtable<Integer,instruction>();
            return;
        }

    }
    
    /**
     * Outputs all contents of ROM to screen in an
     * arbitrary order.
     */
    private void romdump() {
        System.out.println("Displaying instructions stored in rom: ");
        Enumeration<Integer> romitr = rom.keys();
        
        //display all contents of rom
        while (romitr.hasMoreElements()) {
            Integer curaddr = romitr.nextElement();
            System.out.printf("Addr: %3d Instr: ",curaddr.intValue());
            System.out.println(rom.get(curaddr).toString());
        }
        System.out.println("Done.");
    }
    
    /**
     * Outputs all contents of RAM to screen in an
     * arbitrary order.
     */
    private void ramdump() {
        System.out.println("Displaying all loaded addresses in ram: ");
        Enumeration<Integer> ramitr = ram.keys();
        
        //display all contents of ram
        while (ramitr.hasMoreElements()) {
            Integer curaddr = ramitr.nextElement();
            System.out.printf("Addr: %3d Val: %3d\n",curaddr.intValue(),
                    ram.get(curaddr).intValue());
        }
        System.out.println("Done.");
    }
    
    /**Runs program stored in rom*/
    private void run() {
        //flush current ram
        ram = new Hashtable<Integer,Integer>();
        
        int count = 0;
        pCount = 0;
        System.out.println("Running program stored in rom.");
        //while pCount in range
        while (pCount<256) {
            instruction curinst = rom.get(pCount);
            
            if (curinst==null)
                pCount++;
            else {
                curinst.execute();
                count++;
            }
        }
        System.out.println("Done. "+count+" instructions executed.");
    }
    
    /**
     * Initialize RAM by only executing the loadim instructions in
     * the start of ROM.
     */
    private void initram() {
        //flush current ram
        ram = new Hashtable<Integer,Integer>();
        
        int count = 0;
        pCount = 0;
        System.out.println("Initializing ram with load instructions in rom.");
        //while pCount in range
        while (pCount<256) {
            instruction curinst = rom.get(pCount);
            if (curinst==null)
                pCount++;
            
            else if (!curinst.isLoad())
                break;
            
            else {
                count++;
                curinst.execute();
            }
        }
        System.out.println("Done. "+count+" load instructions read.");
    }
    
    /**
     * Display instruction in given ROM address
     */
    private void romget(int addr) {
        System.out.printf("Addr: %3d",addr);
        System.out.println(" "+rom.get(addr).toString());
    }
    
    /**
     * Display contents of given RAM address
     */
    private void ramget(int addr) {
        System.out.printf("Addr: %3d Value: %3d\n",addr,ram.get(addr));
    }
    
    /**Converts given unisigned binary number to integer*/
    public static int binToInt(String bin) {
        return Integer.parseInt(bin,2);
    }
    
    /**Converts given two's coplement 8bit binary to integer*/
    public static int valToInt(String bin) {
        if (bin.length()!=8) {
            System.out.println("virtualOISC:valToInt:invalid input " +
                    bin+", can only handle 8 bit long bit strings");
            System.exit(0);
        }
        String unsigned = bin.substring(1);
        
        if (bin.charAt(0)=='0')
            return binToInt(unsigned);
        
        return binToInt(unsigned)-128;
    }
    
    /**
     * Main function just calls run
     */
    public static void main(String[] args) {
        (new virtualOISC()).start();
    }
}

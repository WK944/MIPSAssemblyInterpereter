package prog2b;

import java.io.*;
import java.util.*;

public class CPU {
    // Memory offsets to specific segments
    public static final int DATA_SEGMENT_ADDRESS = 0x10010000;
    public static final int TEXT_SEGMENT_ADDRESS = 0x00400000;

    // Register file indices used in syscalls
    public static final int $v0 = 2;
    public static final int $a0 = 4;

    // Register file indices that need to be initialized to a specific value
    public static final int $gp = 28;
    public static final int $sp = 29;

    // The integer value of a null character in ASCII
    public static final int NULL_CHAR = 0;

    // The memory is stored as a hashmap, using the address as the key
    private static Map<Integer, Integer> Memory = new HashMap<Integer, Integer>();

    public static void main(String[] args) {
        // Make sure the correct number of arguments was given
        if(args.length != 2) {
            System.out.println("Error: Usage: CPU <TextFile> <DataFile>");
            System.exit(1);
        }

        // Open the text file
        File textFile = new File(args[0]);
        if(!textFile.exists()) {
            System.err.println(args[0]);
            return;
        }

        // Open the data file
        File dataFile = new File(args[1]);
        if(!dataFile.exists()) {
            System.err.println(args[1]);
            return;
        }

        // Build the instruction memory
        try(Scanner scanner = new Scanner(textFile)) {
            for(int i = 0; scanner.hasNextLine(); i += 4) {
                Memory.put(TEXT_SEGMENT_ADDRESS + i, (int)(Long.parseLong(scanner.nextLine(), 16)));
            }
            scanner.close();
        } catch (FileNotFoundException e) {}

        // Build the data memory
        try(Scanner scanner = new Scanner(dataFile)) {
            for(int i = 0; scanner.hasNextLine(); i += 4) {
                // Add the next word to memory in bytes
                String nextWord = scanner.nextLine();
                Memory.put(DATA_SEGMENT_ADDRESS + i, Integer.parseInt(nextWord.substring(6), 16));
                Memory.put(DATA_SEGMENT_ADDRESS + i + 1, Integer.parseInt(nextWord.substring(4, 6), 16));
                Memory.put(DATA_SEGMENT_ADDRESS + i + 2, Integer.parseInt(nextWord.substring(2, 4), 16));
                Memory.put(DATA_SEGMENT_ADDRESS + i + 3, Integer.parseInt(nextWord.substring(0, 2), 16));
            }
            scanner.close();
        } catch (FileNotFoundException e) {}
        
        // Initialize the register file
        int[] registerFile = new int[32];
        Arrays.fill(registerFile, 0);
        registerFile[$gp] = 0x10008000;
        registerFile[$sp] = 0x7fffeffc;

        // Initialize the program counter
        int pc = TEXT_SEGMENT_ADDRESS;

        // Main loop for the single-cycle datapath
        while(true) {
            // End the program when out of instructions
            if(!Memory.containsKey(pc))
                break;

            // Instruction fetch stage
            int instruction = Memory.get(pc);
            
            // Instruction decode stage
            int opcode = getBits(31, 26, instruction);
            int rs = getBits(25, 21, instruction);
            int rt = getBits(20, 16, instruction);
            int rd = getBits(15, 11, instruction);
            int immediate = getBits(15, 0, instruction); // Also works when the instruction uses an offset

            // Execute, memory access, and write back stage. All in one switch statement!!!!!
            switch(opcode) {
                case 0: // R-format
                    switch (getBits(5, 0, instruction)) {
                        case 12: // SYSCALL
                            // Checks the value of register $v0
                            switch(registerFile[$v0]) {
                                case 1: // Print integer
                                    System.out.print(registerFile[$a0]);
                                    break;
                                
                                case 4: // Print string
                                    for(int i = 0; Memory.get(registerFile[$a0] + i) != NULL_CHAR; i++) {
                                        int byteValue = Memory.get(registerFile[$a0] + i);
                                        System.out.print((char) byteValue);
                                    }
                                    break;
                                
                                case 5: // Read integer
                                    try(Scanner inputScanner = new Scanner(System.in)) {
                                        Integer input = Integer.parseInt(inputScanner.nextLine());
                                        registerFile[$v0] = input;
                                    } catch(Exception e) {
                                        displayError("Invalid input for read integer", instruction, pc);
                                    }
                                    break;
                                
                                case 10: // Exit
                                    System.out.println("\n-- program is finished running --");
                                    System.exit(0);
                                
                                default:
                                    displayError("Invalid syscall", instruction, pc);
                            }
                            break;
                        
                        case 32: // ADD
                            registerFile[rd] = registerFile[rs] + registerFile[rt];
                            break;

                        case 34: // SUB
                            registerFile[rd] = registerFile[rs] - registerFile[rt];
                            break;
                        
                        case 36: // AND
                            registerFile[rd] = registerFile[rs] & registerFile[rt];
                            break;
                        
                        case 37: // OR
                            registerFile[rd] = registerFile[rs] | registerFile[rt];
                            break;

                        case 42: // SLT
                            registerFile[rd] = registerFile[rs] < registerFile[rt] ? 1 : 0;
                            break;
                        
                        default:
                            displayError("Invalid function code", instruction, pc);
                    }
                    break;
                
                case 2: // J
                    pc = (getBits(25, 0, instruction) << 2) - 4;  
                    break;
                
                case 4: // BEQ
                    if(registerFile[rs] == registerFile[rt]) {
                        // The instruction offset is just the immediate shifted left by 2
                        pc += (immediate << 2);
                    }
                    break;

                case 5: // BNE
                    if(registerFile[rs] != registerFile[rt]) {
                        // The instruction offset is just the immediate shifted left by 2
                        pc += (immediate << 2);
                    }
                    break;
                
                case 9: // ADDIU
                    registerFile[rt] = registerFile[rs] + immediate;
                    break;

                case 12: // ANDI
                    registerFile[rt] = registerFile[rs] & immediate;
                    break;

                case 13: // ORI
                    registerFile[rt] = registerFile[rs] | immediate;
                    break;

                case 15: // LUI
                    registerFile[rt] = immediate << 16;
                    break;
                
                case 35: // LW
                    registerFile[rt] = Memory.get(registerFile[rs] + immediate);
                    break;

                case 43: // SW
                    Memory.put(registerFile[rs] + immediate, registerFile[rt]);
                    break;
                
                default:
                    displayError("Invalid opcode ", instruction, pc);

            }

            // Increment the program counter
            pc += 4;
        }

        System.out.println("\n-- program is finished running (dropped off bottom) --");
    }

    // Gets bits right through left inclusivly from a number
    public static int getBits(int left, int right, int num) {
        String strnum = Integer.toBinaryString(num);

        // Add leading 0's
        while(strnum.length() < 32) {
            strnum = "0" + strnum;
        } 

        if(left + 1 > strnum.length()) {
            return Integer.parseInt(strnum.substring(31 - left), 2);
        }

        return Integer.parseInt(strnum.substring(31 - left, 31 - right + 1), 2);
    }

    // Prints an error message and exits
    public static void displayError(String error, int instruction, int pc) {
        System.out.println("Error: " + error + " (instruction = " + Integer.toHexString(instruction) + ", pc = " + Integer.toHexString(pc) + ")");
        System.exit(1);
    }
}
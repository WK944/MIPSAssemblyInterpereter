.text
	li $t0, 4
	
	sub $sp, $sp, $t0
	sw $t0, 0($sp)
	
	lw $a0, ($sp)
	li $v0, 1
	syscall
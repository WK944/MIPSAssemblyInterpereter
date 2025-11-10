.text
	li $t0, 1
	li $t1, 2
	li $v0, 1
	
	slt $a0, $t0, $t0
	syscall
	
	slt $a0, $t0, $t1
	syscall
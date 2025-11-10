.text
	li $t0, 1
	li $t1, 2
	li $v0, 1
	
	or $a0, $t0, $t0
	syscall
	
	or $a0, $t0, $t1
	syscall
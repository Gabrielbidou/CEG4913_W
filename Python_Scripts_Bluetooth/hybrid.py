from bluetooth import *
import PCF8591 as ADC
import RPi.GPIO as GPIO
import time
import math
D0 = 17
GPIO.setmode(GPIO.BCM)
ADC.setup(0x48)
GPIO.setup(D0, GPIO.IN)
server_sock = BluetoothSocket(RFCOMM)
server_sock.bind(("", PORT_ANY))
server_sock.listen(1)

port = server_sock.getsockname()[1]

uuid = "9f4a4f86-fe45-48ca-8241-dbfac243ce42"

advertise_service(server_sock, "BTTest", service_id=uuid,
service_classes=[uuid, SERIAL_PORT_CLASS], profiles=[SERIAL_PORT_PROFILE])

while True:
	print("Waiting for connection on RFCOMM channel ", port)
	client_sock, client_info = server_sock.accept()
	print ("Accepted connection from " + str(client_info[0]) +
	 " " + str(client_info[1]))

	try:
		data = client_sock.recv(1024)
		if len(data) == 0: break
		print("received [%s]", data)

		status = 1
		while status == 1:
			#print(str(ADC.read(0)))
			tmp = GPIO.input(D0)
			if tmp != status:
				break
			time.sleep(0.1)

		client_sock.send("OMG!!!")
		print("Sending data")
	except IOError:
		print("IO error")
	except KeyboardInterrupt:
		print("disconnected")
		client_sock.close()
		server_sock.close()
		print("all done")
		break

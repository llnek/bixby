Html5PlaceholderTest = TestCase("Html5PlaceholderTest");

Html5PlaceholderTest.prototype.setUp = function(){
	var sandbox = $('<div></div>');
	sandbox.append('<input type="text" name="firstName" id="name" placeholder="Enter Your Name">');

	this.inputWithPlaceholder = $('#name', sandbox);
	this.emulator = new Html5Emulator(sandbox);
}

Html5PlaceholderTest.prototype.testInputValueIsPopulatedByPlaceholderAttribute = function(){
	this.emulator.emulatePlaceholders();

	assertEquals("Enter Your Name", this.inputWithPlaceholder.val());
}

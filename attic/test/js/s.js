function Html5Emulator(dom){
	this.emulatePlaceholders = function(){
		$('input', dom).each(function(){
			$(this).val($(this).attr('placeholder'));
		});
	};
}

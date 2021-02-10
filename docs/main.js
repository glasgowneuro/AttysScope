function plot_data(r) {
    var miny = -1.5E-3;
    var maxy =  1.5E-3;

    var winx = $(window).width() * 0.8;
    var winy = $(window).height() / 4;

    var axes_opt = {
                axisLabelWidth: 50,
                ticker: function(min, max, pixels, opts, dygraph, vals) {
                  return [{v:1, label:"1"}, {v:-1, label:"-1"}, {v:0, label:"0"}];
                }
    };

    var acc_x = new Dygraph(
        document.getElementById("acc_x"),
	r, {
	    ylabel: 'acc X / m/s^2',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [true, false, false, false, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_y.updateOptions({dateWindow: [minX,maxX]});
                acc_z.updateOptions({dateWindow: [minX,maxX]});
                mag_x.updateOptions({dateWindow: [minX,maxX]});
                mag_y.updateOptions({dateWindow: [minX,maxX]});
                mag_z.updateOptions({dateWindow: [minX,maxX]});
                adc_1.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var acc_y = new Dygraph(
        document.getElementById("acc_y"),
	r, {
	    ylabel: 'acc Y / m/s^2',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, true, false, false, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_x.updateOptions({dateWindow: [minX,maxX]});
                acc_z.updateOptions({dateWindow: [minX,maxX]});
                mag_x.updateOptions({dateWindow: [minX,maxX]});
                mag_y.updateOptions({dateWindow: [minX,maxX]});
                mag_z.updateOptions({dateWindow: [minX,maxX]});
                adc_1.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var acc_z = new Dygraph(
        document.getElementById("acc_z"),
	r, {
	    ylabel: 'acc Z / m/s^2',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, true, false, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_x.updateOptions({dateWindow: [minX,maxX]});
                acc_y.updateOptions({dateWindow: [minX,maxX]});
                mag_x.updateOptions({dateWindow: [minX,maxX]});
                mag_y.updateOptions({dateWindow: [minX,maxX]});
                mag_z.updateOptions({dateWindow: [minX,maxX]});
                adc_1.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var mag_x = new Dygraph(
        document.getElementById("mag_x"),
	r, {
	    ylabel: 'mag X / T',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, true, false, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_x.updateOptions({dateWindow: [minX,maxX]});
                acc_y.updateOptions({dateWindow: [minX,maxX]});
                acc_z.updateOptions({dateWindow: [minX,maxX]});
                mag_y.updateOptions({dateWindow: [minX,maxX]});
                mag_z.updateOptions({dateWindow: [minX,maxX]});
                adc_1.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var mag_y = new Dygraph(
        document.getElementById("mag_y"),
	r, {
	    ylabel: 'mag Y / T',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, false, true, false, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_x.updateOptions({dateWindow: [minX,maxX]});
                acc_y.updateOptions({dateWindow: [minX,maxX]});
                acc_z.updateOptions({dateWindow: [minX,maxX]});
                mag_x.updateOptions({dateWindow: [minX,maxX]});
                mag_z.updateOptions({dateWindow: [minX,maxX]});
                adc_1.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var mag_z = new Dygraph(
        document.getElementById("mag_z"),
	r, {
	    ylabel: 'mag Z / T',
            axes: axes_opt,
	    animatedZooms: true,
	    xlabel: 't/sec',
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, false, false, true, false, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_x.updateOptions({dateWindow: [minX,maxX]});
                acc_y.updateOptions({dateWindow: [minX,maxX]});
                acc_z.updateOptions({dateWindow: [minX,maxX]});
                mag_x.updateOptions({dateWindow: [minX,maxX]});
                mag_y.updateOptions({dateWindow: [minX,maxX]});
                adc_1.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var adc_1 = new Dygraph(
        document.getElementById("adc_1"),
	r, {
	    ylabel: 'ADC 1 / mV',
            axes: { y: {axisLabelWidth: 50} },
	    animatedZooms: true,
	    xlabel: 't/sec',
            drawPoints: true,
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, false, false, false, true, false, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_x.updateOptions({dateWindow: [minX,maxX]});
                acc_y.updateOptions({dateWindow: [minX,maxX]});
                acc_z.updateOptions({dateWindow: [minX,maxX]});
                mag_x.updateOptions({dateWindow: [minX,maxX]});
                mag_y.updateOptions({dateWindow: [minX,maxX]});
                mag_z.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var adc_2 = new Dygraph(
        document.getElementById("adc_2"),
	r, {
	    ylabel: 'ADC 2 / mV',
            axes: { y: {axisLabelWidth: 50} },
	    animatedZooms: true,
	    xlabel: 't/sec',
            drawPoints: true,
	    width: winx,
	    height: winy,
	    valueRange: [ miny, maxy ],
	    visibility: [false, false, false, false, false, false, false, true, false, false],
	    zoomCallback: function(minX, maxX, yRanges) {
                acc_x.updateOptions({dateWindow: [minX,maxX]});
                acc_y.updateOptions({dateWindow: [minX,maxX]});
                acc_z.updateOptions({dateWindow: [minX,maxX]});
                mag_x.updateOptions({dateWindow: [minX,maxX]});
                mag_y.updateOptions({dateWindow: [minX,maxX]});
                mag_z.updateOptions({dateWindow: [minX,maxX]});
                adc_2.updateOptions({dateWindow: [minX,maxX]});
            },
	}
    );

    var reset = function() {
        var rng = adc_1.xAxisExtremes() 
        acc_1.updateOptions({dateWindow: rng});
        acc_2.updateOptions({dateWindow: rng});
        acc_3.updateOptions({dateWindow: rng});
        mag_1.updateOptions({dateWindow: rng});
        mag_2.updateOptions({dateWindow: rng});
        mag_3.updateOptions({dateWindow: rng});
        adc_1.updateOptions({dateWindow: rng});
        adc_2.updateOptions({dateWindow: rng});
    };

    var pan = function(dir) {
        var w = ecg_iii.xAxisRange();
        var scale = w[1] - w[0];
        var amount = scale * 0.25 * dir;
        var rng = [ w[0] + amount, w[1] + amount ];
        acc_1.updateOptions({dateWindow: rng});
        acc_2.updateOptions({dateWindow: rng});
        acc_3.updateOptions({dateWindow: rng});
        mag_1.updateOptions({dateWindow: rng});
        mag_2.updateOptions({dateWindow: rng});
        mag_3.updateOptions({dateWindow: rng});
        adc_1.updateOptions({dateWindow: rng});
        adc_2.updateOptions({dateWindow: rng});
    };

    document.getElementById('full').onclick = function() { reset(); };
    document.getElementById('left').onclick = function() { pan(-1); };
    document.getElementById('right').onclick = function() { pan(+1); };
}

function read_file_contents(fileobj) {
    if (fileobj) {
	var reader = new FileReader();
	reader.readAsText(fileobj, "UTF-8");
	reader.onload = function (evt) {
            document.getElementById("filename").innerHTML = fileobj.name;
	    plot_data(evt.target.result);
	}
	reader.onerror = function (evt) {
	    document.getElementById("message").innerHTML = "error reading file";
	}
    }
}

function upload_file(e) {
    e.preventDefault();
    fileobj = e.dataTransfer.files[0];
    read_file_contents(fileobj)
}

function file_explorer() {
    document.getElementById('selectfile').click();
    document.getElementById('selectfile').onchange = function() {
        fileobj = document.getElementById('selectfile').files[0];
	read_file_contents(fileobj)
    };
}

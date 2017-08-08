+ Color {
	toLong {
		// converts from rgba to long (#rrggbbaa)
		var lr, lg, lb, la;
		#lr, lg, lb, la = [red, green, blue, alpha] * 255;
		^(la.round.asInteger + (lb.round.asInteger << 8) + (lg.round.asInteger << 16) + (lr.round.asInteger << 24));
	}
}

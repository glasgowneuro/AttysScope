# plots all channels of the Attys as recorded by AttysScope2
# www.attys.tech
#
import numpy as np
import pylab as pl
#
data = np.loadtxt('sines6.tsv');
#
fig = pl.figure(1);
#
a = 0;
b = len(data)-1;
# xacc
pl.subplot(811);
pl.plot(data[a:b,0],data[a:b,1]);
pl.xlabel('time/sec');
pl.ylabel('X acc/m/s^2');
# yacc
pl.subplot(812);
pl.plot(data[a:b,0],data[a:b,2]);
pl.xlabel('time/sec');
pl.ylabel('Y acc/m/s^2');
# zacc
pl.subplot(813);
pl.plot(data[a:b,0],data[a:b,3]);
pl.xlabel('time/sec');
pl.ylabel('Z acc/m/s^2');
#
# xmag
pl.subplot(814);
pl.plot(data[a:b,0],data[a:b,4]);
pl.xlabel('time/sec');
pl.ylabel('X mag/T');
#
# ymag
pl.subplot(815);
pl.plot(data[a:b,0],data[a:b,5]);
pl.xlabel('time/sec');
pl.ylabel('Y mag/T');
# zmag
pl.subplot(816);
pl.plot(data[a:b,0],data[a:b,6]);
pl.xlabel('time/sec');
pl.ylabel('Z mag/T');
#
pl.subplot(817);
pl.plot(data[a:b,0],data[a:b,7]);
pl.xlabel('time/sec');
pl.ylabel('ADC1/V');
#
pl.subplot(818);
pl.plot(data[a:b,0],data[a:b,8]);
pl.xlabel('time/sec');
pl.ylabel('ADC2/V');
pl.show();
#

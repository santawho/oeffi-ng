/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.network;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.pte.AvvAachenProvider;
import de.schildbach.pte.AvvAugsburgProvider;
import de.schildbach.pte.BartProvider;
import de.schildbach.pte.BayernProvider;
import de.schildbach.pte.BsvagProvider;
import de.schildbach.pte.BvgProvider;
import de.schildbach.pte.DbMovasProvider;
import de.schildbach.pte.DbProvider;
import de.schildbach.pte.DeutschlandTicketProvider;
import de.schildbach.pte.DingProvider;
import de.schildbach.pte.DsbProvider;
import de.schildbach.pte.DubProvider;
import de.schildbach.pte.GvhProvider;
import de.schildbach.pte.InvgProvider;
import de.schildbach.pte.KvvProvider;
import de.schildbach.pte.LinzProvider;
import de.schildbach.pte.LuProvider;
import de.schildbach.pte.MerseyProvider;
import de.schildbach.pte.MvgProvider;
import de.schildbach.pte.MvvProvider;
import de.schildbach.pte.NasaProvider;
import de.schildbach.pte.NetworkApiProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NsProvider;
import de.schildbach.pte.NvbwProvider;
import de.schildbach.pte.NvvProvider;
import de.schildbach.pte.OebbProvider;
import de.schildbach.pte.PlProvider;
import de.schildbach.pte.RmvProvider;
import de.schildbach.pte.RtProvider;
import de.schildbach.pte.SeProvider;
import de.schildbach.pte.ShProvider;
import de.schildbach.pte.Standard;
import de.schildbach.pte.StvProvider;
import de.schildbach.pte.SydneyProvider;
import de.schildbach.pte.TlemProvider;
import de.schildbach.pte.VbbProvider;
import de.schildbach.pte.VblProvider;
import de.schildbach.pte.VbnProvider;
import de.schildbach.pte.VgnProvider;
import de.schildbach.pte.VmtProvider;
import de.schildbach.pte.VmvProvider;
import de.schildbach.pte.VrnProvider;
import de.schildbach.pte.VrrProvider;
import de.schildbach.pte.VrsProvider;
import de.schildbach.pte.VvmProvider;
import de.schildbach.pte.VvoProvider;
import de.schildbach.pte.VvsProvider;
import de.schildbach.pte.VvvProvider;
import de.schildbach.pte.WienProvider;
import de.schildbach.pte.ZvvProvider;
import okhttp3.HttpUrl;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NetworkProviderFactory {
    private static Map<NetworkId, NetworkProvider> providerCache = new HashMap<>();
    private static final BaseEncoding BASE64 = BaseEncoding.base64();

    private static final byte[] VRS_CLIENT_CERTIFICATE = BASE64.decode("MIITCQIBAzCCEs8GCSqGSIb3DQEHAaCCEsAEghK8MIISuDCCDW8GCSqGSIb3DQEHBqCCDWAwgg1cAgEAMIINVQYJKoZIhvcNAQcBMBwGCiqGSIb3DQEMAQYwDgQIQ7Rbs5ijW2gCAggAgIINKO+doV3Mk9v8OQbKXj0E7+91c5yVk6k/s/UJKbdrS9VloUG+qNknXp+4dZ2B2jInBECaBYcqmaoolv7fGdvECUfYEnihffQ+BWcz6/Ao36LJZQHo3g7wxsLUVU/ALzeDEwJ/kdhI60w+FWwX5Lyd2+t2oCH0uXholM/NxzvNjXu14hoRMUK3OPtrnkiDF37AHsdWCiRXKL+vQULIfLeXnuEfEqC5U0GhaQh190egjacBfL4MN3TgAivyMa/ixQ9WBGMdEMH8vM1khiEzj13WXACOOrUcso72YhWZW0BNTlXuWfyW0CAfPqrMkG1cqdZmDsyjTWAap9oePlPttLmLr7wdzacXN4mt1Ylqjs8TPVAjJBAgDhzYhp+wlJQezSm3zpni4EqRRlMDJDdga7Zlsirbo3waQhWkqPpVl+Zzxjc+6GnPbryNPphohrGt21cYqHnwb0ezm6SbshHnmtUctBlMQMIRoPWA01S7X3vhruui9WK6N2UsjjXUWOPVzXunWi46mi54HUFngQSLvha1QK9ycDolChlxjve1p8H16wpujMwp0e9/fe++h2ID8Tl3hicdTInZ2MgoI9OrsOWbfAwinuzZGPFLZ/OfuMuazb4NmGvUNjkTFKp/dcoYW9oOMQbkbnECiQCZCAz9XEKbNrL8qLifI8H2UMZJcjKkssnM8U0aOKpqxdT5yiqVC6YD5t3lHbWbfrt9BH9QhTIeEFpfr+NRUxyTh9D0cB7j6RtKonqqLrCkGmV+t3+SNaLZUzho0pVe/ApVrMO0o8iyDF2+24wg8WjzMyQcOjDhT2DQAl/MhddJ6kw+qN9iD3uAtNBQiLjOLMgHCItYHL/b4asQJ86HsHFD8D87IgUizd8B07seUfzEC25CpsfgkPeyH70/xR2ysccT/MTJIOmvS0hH1fMLWGjPEqCnAUY0jJN4tE5vLCVkdAfE/KhLrxWCnP5n5EriXjwaSU1S7I9qFoadtvOCjKU/DpohWtY55e3m479EOiiF4/AqgQV/tw2+TOh+gsZMzv0xX8B+qVzYaxlyO6gSg/PsXcteEiQCGzx+1hV6fP4wIhulpYVzNIYLfaed9gl8fajSRG/LQ8aCUN7B/bubGfXx33PAFrjzOtPx/FixfKfqYD4SMEXEU+LYUfdEnSeUhI+7fsjhgpW0Y9fF/2aExp9DAL2xZykCkgGta4FFnIanZBh2ru4CJRcnr0nAulvT+SAtVB0MLAFtToTQft+Lp//t+zH2JM0Rc3C4pX3TGqNeqWrkbpTpSBkkxUF1Vch5MW9BT3SPWEsSV+c5EiR8NgRMr6BeD/4MwdLlHLUXXjeCltlL0T097rW7kF//14WOss0uuBE037tLiN/vIXdY7TboHMP3DUf2Lvq5Ji2c2HZzQrZN8aRuUN7nthw0gs2MmNdEceoePdgU7/z48UnUVjIMxKf5hVKv0Q92mJyDigyK/wN9k0usKydXE76WPx1tnpyhb3Fz0VmZZOVDFaEAvzB2ISKMpoLrOWOv+6VrnSp3FPu+WAbwj3u59M/QYQq1XU4zNW3pMdTMhVGJr0F8kLKbzWDBCho3TQjCWxobe9EHFCpVVOZFWCryLlOmc6LXQinn+AAm3W5khIUzttbzID5CW17V9UU+4PSUngnjaoI0Zn4c3IRr8HndVCIaerN82yU7+xYBt3CKnBzE08o0AjUY4wuDzLnh82c+4bdAlTSGCCDOGdKV2Jikf5gsLz+rp9iz5G/sA36fLeG6B47L4ohv2d94x0AzpcR4lkP0UMyB3/TzWmZIPftf+rNXvMEfDaoqvzCUqw8bT2POykwNpQ5xEaRbIqiGxDVXEnDBOmLTgTTEN2MPYr7oR8iyFvJ3blKDqrXGopVJ9AIC2SziywtCPzB31rxiXQPuiJm3kDtTq1fHfuUv9FCaoNankNgTqHzmbbPDou38p8s1+YzHKU8rv0GuAay1w3AkKYCI7Qig51Ck2HuRXBytFZ5DOQ4N/ZGmAMLxrFy6tDhwbybdaGY6VpDP0hRnfirxaDkREgiqJ841END/z2s/+uf83Fb8tnXMC4AOd0r8qtQWR3pCRp5+yVBpCXWVpd65z/sAyfoGoR2q0UTFxG5SApZDzbmqoFLxY1Bm40uoQp0BZFzID7QQhvBIyVUAn5ddHYb+Kr/S1Vn7ba/TKQVrPKn2gYIxWpv51MzdOrtpAgKSG3sDh1CZ3jwyGmGjbhzDcnO1LthglZeRlTOrBopP11O/oceAG3FIKrr5AtaHCJy0tDa0bt2xAJZnhy2rVrRF/f127s3s3C0L+6Vsjgnhc01wz30nGoZnGrru02OW3KPhOS327gM2ztOozrrCLWMj6w2rj/OxmvhFcGRXkbgOWJI1h2Iys48FtOvXSJHOyjZRIDtfTy8pN0MLa9sMIxxXtQIcwNnpGstz5oo+s7u42XWVqM9VJiNC5tzRRc2v8d55Yo+tCz87N2P9K6HTy2ah/cIj1ZNS+tJfmjn59I3NfP61PpXaF8w2xtxt0+fqVTOTBpCMcdSZDdHRNLdKxvgScsRL1M+R7AT08r9X2I+6hRIx+ZFOjiaIgOAgQrjKjV9mXPHK5+a28h/JH46GfCsvq8sZjXOv31j/3F2achLNSK79Lnr9mOBOIRZLYbKAaN39QmmxJG8H4uDkCon2CnL6yUiH91UvGbrmevB29X2XnBDNfiY+mkpqZII6zgS8NqxY92k/DBPHDW29qQ8kSuFtSzwAWL2GLAvnIlogh4zXpqeminMBQ77Z3V7QaJMVHbl2yI31t3buapyUPB2+EacsqihwBPbx0yhOaB1w2cvmjpYdt+sQdDyQaUExq0XP/zxhoCjObBbXnSovAls2ZFDzhTh29uQzjeVRe5bgBPw8b3g2S/nqwXWSpAWaRJKPHKVeDOPboiq+m4X/JA904MR9hAvr4b9QJD5zUGKi+mgsGrURJwIKMX1mWVj3miSgpmNvaC/PXEBM2QQg73WPHr8r2vTSTi1VS2FsqPkZ2k7brnnBfbrrH0ghpS85ZukmsG9h9pOIPw1SQvnOizBcOXsMlh3uR/L/VnMSrN0UcRoEMhWlQmAg/fd6B8egRmD/xbdAQwuP46uAKaKzk33XZDr8xXy2XLen5xTUjNZZNDwuW1Q2kgbFMAIXlMHIlaVmtHDLNgybpCd/Ch7x+gbh38zev4SB7gOAA8RMRee/FVYYlP8Urbx4NauYBLtAIMgl4KfNWJHhEfge8MeNblZigqjG+IDXTCr7b/poAgqmQuBofxpvm2dIpJdNrGv+u59SlfAiLotVJDL8T2BiRrxdJ1N0cLcUTu83h9SVVAeCTwIo1KJTxaHUWoUKC94XJEI1buOFXeiCLOFdxye7ApiCwUiL/GklajAjAHqx6aln9uGfWU5N63TtR1eusT08ywBKO8BKLO77OYpTpbGu69ek/ktjCj2QVh7yci7X6kZKuyKmfZLSBjTeDwhofAcZ0pRdBzoLulLE1omeqS1ObwQUfjSOfJl9AP4/1VEmFD73I0MP6fKVnmX9I6d21XOQO9jUOmxeNrC8R4knJGNDc6/rotpuDXBMJMqXw5E79vNpbOsU8dOf7bMSvAfDQpX2lZ/wuw/upaRhJK+W5r/6p/eqBPP5CBp3tzGh97KI67XhN+OJ53YxtB//Rspim27v91erQL3M4AUb1i+piraM5nSrqtmA2ERX6PGz/ZazULC2xZxTOkeSYj0pzFR/PraFUG6u7AwmmH2lAlKusuum6zxjrqxhvAWyEF3N5KCesovYRSow5EZXVum1If2ukkWxNzTwuZteAKgyNd5kYepnnQLVBul0FyQGFV+OgbGldXmDTvtXUoX+qpHs4NzNhOkkd9pIjdtKOc/TR1SQsgK+V0RCTUktyWbke1AngLz2XdfBIkovEEb91JlEMTHNqn+2qZgN7ZKs5bc8AWqy7IJOABDqmB5QZojZK1WSMenh6mHohuC2lr2dSxjaDW9GXI9HcUxG/ErbwDxsdrTdkYIW8QDLUlX8kugaPrBe80uKI1hP/mjRhVKceIH8Vwvv3D3dibBzWbWqENwQXNK/f2xeWW1+/ega6b/2NrT4Nm8RY8y56zaLs0Ly+uumVFYePebRwTeaVBj9iBKJ7tMaJrdcPGmkf4Eiv5Hi+5qTR5OpBwXY8oq6sL87DI45ZsMxnpouwC1/KvdUN6ZUk0rA/zsWJvH/s77x8mVVAeNVZrakXGZ1XhlhDCHNVvMzXl3i+FkveWzF4B7HP0b9V89QnAasMXAJ8kRgTKfYOXE3bly1u52VPO5zkcJBVZ0UYDAGJfa6S56buViCo801c3ABBzuTa9p/LIl5G1z25RTItAsmHzELdCxxbwq7TQSiYZgL0/xucmXsCqL5CJYgUHKzb5zDFGc8laT9bVKpR654jakJi7TP0wr2sSbGl67ASxSokkp/qPcEwiCUdAe5/zw2NchhIewES1PGuyMus4Ne35Vxmlic7vOZn6lNs6uilhshMIIFQQYJKoZIhvcNAQcBoIIFMgSCBS4wggUqMIIFJgYLKoZIhvcNAQwKAQKgggTuMIIE6jAcBgoqhkiG9w0BDAEDMA4ECOuiTQxj9rysAgIIAASCBMg0Gffj1R7f/mhdYAlsazH9aUH7Z8SNuD0s2jWhuvCn8IYQEB1DHdBpWtXAG2wLUOwsWp/OlIxIiP3eFXjGNEillsATJbIRj6f8D2m95zYdO2691DBjVtcp+ldsXfUp8MpZM/FzsKI9ovUFJCIpgOLaxb81xClSdW0coW3S1IAfaxQX6rMDEOwU9w1FRMuItexgdVHc+FBERZBDsuzeq3NNoJsbu1CLxqVRzmGzOQHDO2tvHMlDgf/eM9RzgQ4RUiP+5TggkeXrEz4Gx4awFOC6fOM3+VoWKUvhR87yC7NsKccOts7ByHtKUQTG0KZmyiDRqBpyEDqcaX/MMb1jEen6QhdUn2hi8LG+3f0sDCenDdAezOKB5EqD/kctM6vU8RXE9jqMIBZJMAGKeYiMlO6r6X4TjZR0onTS56DRUFlGchlpCAUDGbBq297IEIg+P5h+fUiSmLflz7xGARqVwnLycD9b1Zy9SOLpXHbGL6MiXRt3cEh6bdG4JMAzl8qzxTOjT19Nl4nbJjjt3v5f6vP4PLQyPXIIqQWakXoHr53ey7SFojV+IuKZCs/vyLGVioAEmMK49YtkUjlBsAj8IBasUiQ36w2ABLae44+ftkMTLKO7/FiGpNFP1WocHF0NsxQr+u50oJ+wIpT79F6oT0/tKku3KDkrYbhvCUvwvASeO6JbvpUC3RsTLP4yh33Q4Kdr39ylg/aOow151VPz+wpDTkzxZNJku1qSVAyiIvHzdOZe9BT0g2ZE1BfWcCRFMEWPF3eBdhAUPMPBqyLgUkSChWGvsSWYtsZ0ZRXwftggz2AUDDLuW/4iEKLpAxZBRROiMxpgkn+XzC1dC0Dx9Qma0d12CJVc8AqXOt1/tkuqShXREZfAFw3zh8kpCDg1K1ptUsdQ5jsQjO8/6VlVH8PxLCuOqxs/4GHAKDxsv75kF/QI/NwQ6lVJoiArf5FVqSliQsgfWk6NgwGPSaOfyg5D4jWbOcyZMJEWYFISLoq2SW/rAgQFcNDr9ZeNNCO5++ovbItba5tyRNznTPPm9L79wGLr0Pb4rP8yZ9aqmwTxdzPArt4vnQ38wd7kZx8l/nX5zaRb5fqDPBE7J1tpE+CJYof/zXCG9wRp12xQ29n+xCqqRvmHAu5U1bm+AHv6mXscQVzAr4+yoaK6SKJq9tkOfXN9hrQq+3i06tuoGFeYnkf71DgBvnUcv5x3JBJdMUGDyg6giYO5Oe53PM27Dk805X1sP6aZxKGQdxcMfs/g/KxYHonGeP738qHQ6/+GIBQuS8Ee8Ox7Kp/vtNAtnzqLilwI281oEn0az/JvVuERhoQ9YTMp1OYgRdVEvtOmzKj5nSbU4TdU3JH4r8JPC6W7WK7dP9T7OxxD/Kj+1kzNakd2tc0WoVSTEcIQTXWqx9Q+cGXx8Z5HEZ+VC+TwsxAyCPuK+oqt/cEy7BFe4yVwWLO1twsoUSMHhta84dyKhdC9HNR83ZfxE5dmN/wG5hXVQHWCFyW2Du/i5oA6cIPT2UJ/sbcMRRA2lT9jUpVK4LECjcoC3a7qB+fBWUlp4nCiqk2p4q/P/aiTjGEZ9cjFcjdy9k1tzLGXduvypodtrCZswn5eHTkA3fQVZIM+T9VSNSo+ZJbA/kgxJTAjBgkqhkiG9w0BCRUxFgQU+fQ2V0LUV95cAYEUIoaSPs+5fLUwMTAhMAkGBSsOAwIaBQAEFM/qlSuXJpdS/NknWCgbuD3r770iBAgRMNsQV7CzIgICCAA=");

    public static synchronized NetworkProvider provider(final NetworkId networkId) {
        Standard.setDoNotUseSpecialLineStyles(Application.getInstance().getSharedPreferences()
                .getBoolean(Constants.PREFS_KEY_USER_INTERFACE_NOPROVIDERCOLORS_ENABLED, false));

        final NetworkProvider cachedNetworkProvider = providerCache.get(networkId);
        if (cachedNetworkProvider != null)
            return cachedNetworkProvider;

        final NetworkApiProvider networkProvider = forId(networkId);
        if (networkId != NetworkId.PL)
            networkProvider.setUserAgent(Application.getUserAgent());
        networkProvider.setUserInterfaceLanguage(Locale.getDefault().getLanguage());
        networkProvider.setMessagesAsSimpleHtml(true);
        providerCache.put(networkId, networkProvider);
        return networkProvider;
    }

    private static NetworkApiProvider forId(final NetworkId networkId) {
        if (networkId.equals(NetworkId.RT))
            return new RtProvider();
        else if (networkId.equals(NetworkId.DEUTSCHLANDTICKET))
            return new DeutschlandTicketProvider();
        else if (networkId.equals(NetworkId.DB))
            return new DbProvider.Fernverkehr();
        else if (networkId.equals(NetworkId.DBREGIO))
            return new DbProvider.Regio();
        else if (networkId.equals(NetworkId.DBINTERNATIONAL))
            return new DbProvider.International();
//        else if (networkId.equals(NetworkId.DBDEUTSCHLANDTICKETWEB))
//            return new DbWebProvider.DeutschlandTicket();
//        else if (networkId.equals(NetworkId.DBWEB))
//            return new DbWebProvider.Fernverkehr();
//        else if (networkId.equals(NetworkId.DBREGIOWEB))
//            return new DbWebProvider.Regio();
        else if (networkId.equals(NetworkId.DBDEUTSCHLANDTICKETMOVAS))
            return new DbMovasProvider.DeutschlandTicket();
        else if (networkId.equals(NetworkId.DBMOVAS))
            return new DbMovasProvider.Fernverkehr();
        else if (networkId.equals(NetworkId.DBREGIOMOVAS))
            return new DbMovasProvider.Regio();
//        else if (networkId.equals(NetworkId.DBHAFAS))
//            return new DbHafasProvider.Fernverkehr("{\"type\":\"AID\",\"aid\":\"n91dB8Z77MLdoR0K\"}",
//                    "bdI8UVj40K5fvxwf".getBytes(Charsets.UTF_8));
//        else if (networkId.equals(NetworkId.DBREGIOHAFAS))
//            return new DbHafasProvider.Regio("{\"type\":\"AID\",\"aid\":\"n91dB8Z77MLdoR0K\"}",
//                    "bdI8UVj40K5fvxwf".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.BVG))
            return new BvgProvider("{\"aid\":\"1Rxs112shyHLatUX4fofnmdxK\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.VBB))
            return new VbbProvider("{\"type\":\"AID\",\"aid\":\"hafas-vbb-apps\"}",
                    "RCTJM2fFxFfxxQfI".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.NVV))
            return new NvvProvider("{\"type\":\"AID\",\"aid\":\"Kt8eNOH7qjVeSxNA\"}");
        else if (networkId.equals(NetworkId.BAYERN))
            return new BayernProvider();
        else if (networkId.equals(NetworkId.MVV))
            return new MvvProvider();
        else if (networkId.equals(NetworkId.INVG))
            return new InvgProvider("{\"type\":\"AID\",\"aid\":\"GITvwi3BGOmTQ2a5\"}",
                    "ERxotxpwFT7uYRsI".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.AVV_AUGSBURG))
            return new AvvAugsburgProvider("{\"type\":\"AID\",\"aid\":\"jK91AVVZU77xY5oH\"}");
        else if (networkId.equals(NetworkId.VGN))
            return new VgnProvider(HttpUrl.parse("https://efa.vgn.de/vgnExt_oeffi/"));
        else if (networkId.equals(NetworkId.VVM))
            return new VvmProvider();
        else if (networkId.equals(NetworkId.VMV))
            return new VmvProvider();
        else if (networkId.equals(NetworkId.SH))
            return new ShProvider("{\"aid\":\"r0Ot9FLFNAFxijLW\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.GVH))
            return new GvhProvider();
        else if (networkId.equals(NetworkId.BSVAG))
            return new BsvagProvider();
        else if (networkId.equals(NetworkId.VBN))
            return new VbnProvider("{\"aid\":\"rnOHBWhesvc7gFkd\",\"type\":\"AID\"}",
                    "SP31mBufSyCLmNxp".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.NASA))
            return new NasaProvider("{\"type\":\"AID\",\"aid\":\"nasa-apps\"}");
        else if (networkId.equals(NetworkId.RMV))
            return new RmvProvider("{\"type\":\"AID\",\"aid\":\"ikfr894fkfddXxA0U\"}");
        else if (networkId.equals(NetworkId.VMT))
            return new VmtProvider("{\"aid\":\"vj5d7i3g9m5d7e3\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.VVO))
            return new VvoProvider(HttpUrl.parse("https://efa.vvo-online.de/Oeffi/"));
        else if (networkId.equals(NetworkId.VRR))
            return new VrrProvider();
        else if (networkId.equals(NetworkId.VRS))
            return new VrsProvider(VRS_CLIENT_CERTIFICATE);
        else if (networkId.equals(NetworkId.AVV_AACHEN))
            return new AvvAachenProvider("{\"id\":\"AVV_AACHEN\",\"l\":\"vs_oeffi\",\"type\":\"WEB\"}",
                    "{\"type\":\"AID\",\"aid\":\"4vV1AcH3N511icH\"}");
        else if (networkId.equals(NetworkId.MVG))
            return new MvgProvider();
        else if (networkId.equals(NetworkId.VRN))
            return new VrnProvider();
        else if (networkId.equals(NetworkId.VVS))
            return new VvsProvider();
        else if (networkId.equals(NetworkId.DING))
            return new DingProvider();
        else if (networkId.equals(NetworkId.KVV))
            return new KvvProvider(HttpUrl.parse("https://projekte.kvv-efa.de/oeffi/"));
        else if (networkId.equals(NetworkId.NVBW))
            return new NvbwProvider();
        else if (networkId.equals(NetworkId.VVV))
            return new VvvProvider();
        else if (networkId.equals(NetworkId.OEBB))
            return new OebbProvider("{\"type\":\"AID\",\"aid\":\"OWDL4fE4ixNiPBBm\"}");
        else if (networkId.equals(NetworkId.WIEN))
            return new WienProvider();
        else if (networkId.equals(NetworkId.LINZ))
            return new LinzProvider();
        else if (networkId.equals(NetworkId.STV))
            return new StvProvider();
        else if (networkId.equals(NetworkId.VBL))
            return new VblProvider();
        else if (networkId.equals(NetworkId.ZVV))
            return new ZvvProvider("{\"type\":\"AID\",\"aid\":\"hf7mcf9bv3nv8g5f\"}");
        else if (networkId.equals(NetworkId.LU))
            return new LuProvider("{\"type\":\"AID\",\"aid\":\"SkC81GuwuzL4e0\"}");
        else if (networkId.equals(NetworkId.NS))
            return new NsProvider();
        else if (networkId.equals(NetworkId.DSB))
            return new DsbProvider("{\"type\":\"AID\",\"aid\":\"irkmpm9mdznstenr-android\"}");
        else if (networkId.equals(NetworkId.SE))
            return new SeProvider("{\"type\":\"AID\",\"aid\":\"h5o3n7f4t2m8l9x1\"}");
        else if (networkId.equals(NetworkId.TLEM))
            return new TlemProvider();
        else if (networkId.equals(NetworkId.MERSEY))
            return new MerseyProvider();
        else if (networkId.equals(NetworkId.PL))
            return new PlProvider("{\"type\":\"AID\",\"aid\":\"DrxJYtYZQpEBCtcb\"}");
        else if (networkId.equals(NetworkId.DUB))
            return new DubProvider();
        else if (networkId.equals(NetworkId.BART))
            return new BartProvider("{\"type\":\"AID\",\"aid\":\"kEwHkFUCIL500dym\"}");
        else if (networkId.equals(NetworkId.SYDNEY))
            return new SydneyProvider();
        else
            throw new IllegalArgumentException(networkId.name());
    }
}

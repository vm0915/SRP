package Client;

import SRP.Wrapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.ArrayList;
import java.util.Scanner;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private ClientField client;

    ClientHandler() { }

    private String getLogin(){
        Scanner sc = new Scanner(System.in);
        String login = "";
        boolean cnt = false;
        do {
            login = sc.nextLine();
            if(login.length()<3){
                System.out.println("Логин должен быть не меньше 3 символов!");
            }else{
                cnt = true;
            }
        }while (!cnt);
        return login;
    }

    private String getPassword(){
        Scanner sc = new Scanner(System.in);
        String password = "";
        boolean cnt = false;
        do {
            password = sc.nextLine();
            if(password.length()<3){
                System.out.println("Пароль должен быть не меньше 3 символов!");
            }else{
                cnt = true;
            }
        }while (!cnt);
        return password;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws RuntimeException {
        String login = ""; // Имя пользователя
        String password = ""; // Пароль
        System.out.println("Логин: ");
        login = getLogin(); // Проверяем размер логина
        System.out.println("Пароль: ");
        password = getPassword(); // Проверяем размер пароля
        client = new ClientField(login, password); // Если данные введены верно - создаём клиента
        ArrayList<Object> data = new ArrayList<Object>();
        data.add(client.getId());
        data.add(client.getSalt());
        data.add(client.getPass_verifier());
        Wrapper wrapper = new Wrapper(1, data, null);
        ctx.write(wrapper);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.flush();
        ArrayList<Object> data = new ArrayList<Object>();
        data.add(client.getId());
        data.add(client.compA());
        ctx.write(new Wrapper(2, data, null));
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof String) {
            System.out.println(msg);
        } else {
            Wrapper msg1 = (Wrapper) msg;
            switch (msg1.getStage()) {
                case 1:
                    String str = (String) msg1.getObject();
                    str = str.split(client.getSalt())[0];
                    long b = Long.parseLong(str);
                    if (b != 0) { // Клиент проверяет, что B - не ноль
                        client.setbBig(b);
                        if (!"0".equals(client.scrambler())) { //И клиент и сервер вычисляют u=H(A,B)
                            client.keyComp(); // Клиент вычисляет общий ключ сессии К
                            String a = client.confirmationHash();
                            ArrayList<Object> data = new ArrayList<Object>();
                            data.add(client.getId());
                            data.add(a);
                            ctx.write(new Wrapper(3, data, null));
                        } else { //Если u - ноль, соединение прерывается
                            ctx.close();
                            throw new RuntimeException("Ошибка в 1 фазе: U - ноль");
                        }
                    } else {
                        ctx.close();
                        throw new RuntimeException("Ошибка в 1 фазе: В - ноль");
                    }
                    break;
                case 2: // 2 фаза("генерация подтверждения")
                    String s = (String) msg1.getObject();
                    String s2 = client.getM(); // Клиент вычисляет М
                    if (s.equals(s2)) {
                        ArrayList<Object> data = new ArrayList<Object>();
                        data.add(client.getId());
                        data.add(client.compR()); // М
                        ctx.write(new Wrapper(4, data, null)); // И отправляет серверу
                    } else {
                        ctx.close();
                        throw new RuntimeException("Ошибка во 2 фазе: подтверждение не пройдено");
                    }
                    break;
                case 3:
                    if (msg1.getObject().equals(client.getR())) {  // Если Ms = Mk, то успех и сервер отправляет клиенту R=H(A,M,k)
                        System.out.println("Сервер прошёл проверку.");
                    } else {
                        ctx.close();
                        throw new RuntimeException("Ошибка в 3 фазе: подтверждение не пройдено");
                    }
                    break;
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) { ctx.flush(); }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
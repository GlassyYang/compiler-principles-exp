int[12] a;
float[22] b;
int a;

struct abd{
    int a;
    int b;
    int[10] c;
};

function abs(int a int b)
{
    int temp;
    int[12] temp;
    int[12][13] temp;
    int bc;
    struct abd c;
    temp[1][2] = a + b;
    a = b;
    b = temp;
    temp[11] = a - b;
    while(a == b){
        a = c;
        b = c;
        a = a * b;
    }
    if(a <= 1){
        a = 1;
        if(b > 0){
            a = 2;
        }
    }
    if(a != 1){
        a = 1;
    }else{
        a = 2;
    }
}

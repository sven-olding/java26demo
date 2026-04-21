import numpy as np
import time

def my_dot(a, b):
    """
   Compute the dot product of two vectors

    Args:
      a (ndarray (n,)):  input vector
      b (ndarray (n,)):  input vector with same dimension as a

    Returns:
      x (scalar):
    """
    x=0
    for i in range(a.shape[0]):
        x = x + a[i] * b[i]
    return x


if __name__ == "__main__":
    a = np.array([1, 2, 3, 4])
    b = np.array([-1, 4, 3, 2])
    print(f"my_dot(a, b) = {my_dot(a, b)}")

    np.random.seed(1)
    a = np.random.rand(10000000)  # very large arrays
    b = np.random.rand(10000000)

    tic = time.time()  # capture start time
    c = np.dot(a, b)
    toc = time.time()  # capture end time

    print(f"np.dot(a, b) =  {c:.4f}")
    print(f"Vectorized version duration: {1000*(toc-tic):.4f} ms ")

    tic = time.time()  # capture start time
    c = my_dot(a,b)
    toc = time.time()  # capture end time

    print(f"my_dot(a, b) =  {c:.4f}")
    print(f"loop version duration: {1000*(toc-tic):.4f} ms ")

    del(a)
    del(b)

# python .\vectorization.py
# > my_dot(a, b) = 24
# > np.dot(a, b) =  2501072.5817
# > Vectorized version duration: 4.8516 ms
# > my_dot(a, b) =  2501072.5817
# > loop version duration: 3371.4411 ms